import groovy.transform.Field

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.cjk.CJKWidthFilter
import org.apache.lucene.analysis.ja.JapaneseBaseFormFilter
import org.apache.lucene.analysis.ja.JapaneseKatakanaStemFilter
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.SimpleFragmenter
import org.apache.lucene.search.highlight.SimpleHTMLFormatter
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.QueryBuilder

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import org.apache.poi.extractor.ExtractorFactory

// Full-text search over the document directory: updateIndex() builds/updates a
// Lucene index at <docDir>/.search-index/, search() queries it.
//
// Next to the Lucene index sits files.meta: the schema version on its first line,
// then one "<mtime>\t<relative path>" line per indexed file. That file - not
// Lucene - is what makes updateIndex() incremental.

@Field static final String INDEX_DIR_NAME = '.search-index'

// Bump whenever the analyzer or the indexed fields change so that old and new
// tokens no longer match; updateIndex() then rebuilds the index from scratch
// instead of incrementally. SearchIndexSpec pins the current tokenization, so
// forgetting the bump fails the build.
@Field static final int INDEX_SCHEMA_VERSION = 1

@Field private static final String LUCENE_SUBDIR_NAME = 'lucene'
@Field private static final String META_FILE_NAME = 'files.meta'
@Field private static final String META_VERSION_PREFIX = 'version='

@Field private static final String FIELD_PATH = 'path'
@Field static final String FIELD_FILENAME = 'filename'
@Field static final String FIELD_CONTENT = 'content'
// A file whose name matches ranks above one that merely mentions the keyword.
@Field private static final Map<String, Float> SEARCH_FIELD_BOOSTS = [(FIELD_FILENAME): 2.0f, (FIELD_CONTENT): 1.0f]

// Kuromoji loads its dictionary (a few hundred ms, several MB) when the first
// JapaneseTokenizer is created, so one analyzer is shared per class loader.
@Field private static Analyzer cachedAnalyzer

@Field private static final Set<String> TEXT_EXTENSIONS = ['md', 'markdown', 'txt'] as Set
@Field private static final Set<String> PDF_EXTENSIONS = ['pdf'] as Set
@Field private static final Set<String> OFFICE_EXTENSIONS = ['docx', 'xlsx', 'pptx'] as Set

// Keeps one pathological file (e.g. a huge PDF) from blowing up the index.
@Field private static final int MAX_CONTENT_CHARS = 2_000_000
@Field private static final int SNIPPET_CHARS = 160
@Field private static final int DEFAULT_MAX_RESULTS = 100

// Filename field only: also split "QuarterlyReport2024" into Quarterly/Report/2024.
@Field private static final int FILENAME_SPLIT_FLAGS =
        WordDelimiterGraphFilter.GENERATE_WORD_PARTS | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS |
        WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS

/**
 * One search result: the matched file, its path relative to the document
 * directory (forward-slash separated, as stored in the index), and a content
 * snippet around the match (empty when the file only matched by name).
 */
class SearchHit {
    File file
    String relativePath
    String snippet
}

/**
 * Extracts searchable text from a file by extension: Markdown/plain text directly,
 * PDF via PDFBox, docx/xlsx/pptx via Apache POI. Any other extension, and any
 * file that fails to parse, yields '' rather than throwing, so one bad file only
 * costs its own content.
 *
 * Catches Throwable, not Exception: PDFBox's lazy AWT font-mapping init throws an
 * Error (NoClassDefFoundError) under Freeplane's script sandbox, and that class
 * then stays broken for the whole JVM session - letting it escape would abort
 * every indexing run on its first PDF.
 */
def static String extractText(File file) {
    def ext = extensionOf(file.name)
    try {
        String text
        if (ext in TEXT_EXTENSIONS) {
            text = Utils.readPage(file)
        } else if (ext in PDF_EXTENSIONS) {
            text = readPdfText(file)
        } else if (ext in OFFICE_EXTENSIONS) {
            text = readOfficeText(file)
        } else {
            return ''
        }
        return text.length() > MAX_CONTENT_CHARS ? text.substring(0, MAX_CONTENT_CHARS) : text
    } catch (Throwable ignored) {
        return ''
    }
}

/**
 * Incrementally updates the index: new/modified files (per the recorded mtime)
 * are (re-)extracted, deleted files are dropped, unmodified files are left
 * alone. Rebuilds everything when the on-disk schema version is stale. A
 * no-op when another update currently holds the index lock.
 */
def static void updateIndex(File docDir) {
    def indexDir = new File(docDir, INDEX_DIR_NAME)
    def luceneDir = new File(indexDir, LUCENE_SUBDIR_NAME)
    luceneDir.mkdirs()
    def metaFile = new File(indexDir, META_FILE_NAME)

    FSDirectory.open(luceneDir.toPath()).withCloseable { directory ->
        IndexWriter writer
        try {
            writer = new IndexWriter(directory, new IndexWriterConfig(sharedAnalyzer()))
        } catch (Exception ignored) {
            return
        }

        def meta = loadMeta(metaFile)
        def schemaChanged = meta.version != INDEX_SCHEMA_VERSION
        Map<String, Long> knownMTimes = schemaChanged ? [:] : meta.mtimes
        def seenPaths = new HashSet<String>()

        writer.withCloseable {
            if (schemaChanged) writer.deleteAll()

            eachIndexableFile(docDir) { file, relPath ->
                seenPaths << relPath
                def mtime = file.lastModified()
                if (knownMTimes[relPath] == mtime) return

                def doc = new Document()
                doc.add(new StringField(FIELD_PATH, relPath, Store.YES))
                doc.add(new TextField(FIELD_FILENAME, file.name, Store.YES))
                doc.add(new TextField(FIELD_CONTENT, extractText(file), Store.YES))
                writer.updateDocument(new Term(FIELD_PATH, relPath), doc)
                knownMTimes[relPath] = mtime
            }

            (knownMTimes.keySet() - seenPaths).each { relPath ->
                writer.deleteDocuments(new Term(FIELD_PATH, relPath))
                knownMTimes.remove(relPath)
            }

            writer.commit()
        }

        // After the commit only, so an interrupted run is redone in full next time.
        saveMeta(metaFile, knownMTimes)
    }
}

/**
 * Files matching every whitespace-separated keyword (AND, case-insensitive) in
 * content or file name. Keywords are literal text, never query syntax. Empty
 * when the index doesn't exist yet or no keyword tokenizes to anything.
 */
def static List<SearchHit> search(File docDir, String queryText, int maxResults = DEFAULT_MAX_RESULTS) {
    def keywords = queryText?.trim() ? queryText.trim().split(/\s+/) as List : []
    if (!keywords) return []

    def luceneDir = new File(new File(docDir, INDEX_DIR_NAME), LUCENE_SUBDIR_NAME)
    if (!luceneDir.exists()) return []

    def analyzer = sharedAnalyzer()
    def query = andQuery(analyzer, keywords)
    if (!query) return []

    // A reader per search costs milliseconds on a personal-sized index and always
    // sees the latest commit.
    return FSDirectory.open(luceneDir.toPath()).withCloseable { directory ->
        DirectoryReader.open(directory).withCloseable { reader ->
            def searcher = new IndexSearcher(reader)
            searcher.search(query, maxResults).scoreDocs.collect { scoreDoc ->
                toHit(searcher.doc(scoreDoc.doc), query, analyzer, docDir)
            }
        }
    }
}

/** The tokens the given field's analyzer produces for text - what a keyword is matched against. */
def static List<String> tokenize(String field, String text) {
    def tokens = []
    sharedAnalyzer().tokenStream(field, text).withCloseable { TokenStream stream ->
        def term = stream.addAttribute(CharTermAttribute)
        stream.reset()
        while (stream.incrementToken()) tokens << term.toString()
        stream.end()
    }
    return tokens
}

/**
 * Loads the analyzer (and Kuromoji's dictionary) ahead of the first query. The
 * Search dialog calls this from its background index refresh, since an
 * up-to-date index gives updateIndex() nothing to tokenize. Best effort: any
 * failure is reported by the search that actually needs the analyzer.
 */
def static void warmUp() {
    try {
        tokenize(FIELD_CONTENT, 'warm up')
    } catch (Throwable ignored) {
        // See above.
    }
}

// --- Private helper methods ---

private static synchronized Analyzer sharedAnalyzer() {
    if (cachedAnalyzer == null) {
        cachedAnalyzer = new PerFieldAnalyzerWrapper(newJapaneseAnalyzer(false), [(FIELD_FILENAME): newJapaneseAnalyzer(true)])
    }
    return cachedAnalyzer
}

/**
 * Kuromoji does morphological analysis rather than bigram splitting, so "京都"
 * doesn't match "東京都", the dictionary form "読む" matches "読んだ", and
 * SEARCH mode splits compounds ("関西国際空港" -> 関西/国際/空港). Non-CJK text
 * is tokenized word by word and lowercased.
 */
private static Analyzer newJapaneseAnalyzer(boolean forFilename) {
    return new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            // discardPunctuation: "." "_" "-" are token breaks, so "Report.md" is
            // searchable as "Report". discardCompoundToken: emit only the parts of a
            // compound, giving the flat token sequence fieldQueryFor()'s phrase
            // queries assume.
            def tokenizer = new JapaneseTokenizer(null, true, true, JapaneseTokenizer.Mode.SEARCH)
            TokenStream stream = new JapaneseBaseFormFilter(tokenizer) // "読んだ" -> "読む"
            stream = new CJKWidthFilter(stream)                       // full/half-width forms
            stream = new JapaneseKatakanaStemFilter(stream)           // "コンピューター" -> "コンピュータ"
            if (forFilename) {
                // Before lowercasing, since it splits on case changes.
                stream = new WordDelimiterGraphFilter(stream, FILENAME_SPLIT_FLAGS, CharArraySet.EMPTY_SET)
            }
            stream = new LowerCaseFilter(stream)
            return new TokenStreamComponents(tokenizer, stream)
        }
    }
}

private static String extensionOf(String name) {
    def dot = name.lastIndexOf('.')
    return dot < 0 ? '' : name.substring(dot + 1).toLowerCase()
}

private static String readPdfText(File file) {
    return PDDocument.load(file).withCloseable { doc ->
        def stripper = new PDFTextStripper()
        stripper.sortByPosition = true
        return stripper.getText(doc)
    }
}

private static String readOfficeText(File file) {
    def extractor = ExtractorFactory.createExtractor(file)
    try {
        return extractor.text ?: ''
    } finally {
        extractor.close()
    }
}

/** Visits every regular file under docDir, skipping dot-directories (the index itself, .git, ...). */
private static void eachIndexableFile(File docDir, Closure action) {
    walkFiles(docDir, docDir.toPath(), action)
}

private static void walkFiles(File dir, java.nio.file.Path base, Closure action) {
    dir.eachFile { f ->
        if (f.isDirectory()) {
            if (f.name.startsWith('.')) return
            walkFiles(f, base, action)
        } else if (f.isFile()) {
            def relPath = base.relativize(f.toPath()).toString().replace(File.separator, '/')
            action(f, relPath)
        }
    }
}

/** Every keyword must match (AND); a keyword matches in either field (OR, boosted). */
private static andQuery(analyzer, List<String> keywords) {
    def builder = new BooleanQuery.Builder()
    def any = false
    keywords.each { keyword ->
        def clause = orAcrossFieldsQuery(analyzer, keyword)
        if (clause) {
            builder.add(clause, BooleanClause.Occur.MUST)
            any = true
        }
    }
    return any ? builder.build() : null
}

private static orAcrossFieldsQuery(analyzer, String keyword) {
    def builder = new BooleanQuery.Builder()
    def any = false
    SEARCH_FIELD_BOOSTS.each { field, boost ->
        def fieldQuery = fieldQueryFor(analyzer, field, keyword, boost)
        if (fieldQuery) {
            builder.add(fieldQuery, BooleanClause.Occur.SHOULD)
            any = true
        }
    }
    return any ? builder.build() : null
}

/**
 * The keyword's tokens as a phrase (TermQuery for one token, PhraseQuery for
 * several, null for none), so "検索機能" matches "検索機能" but not text that
 * merely contains 検索 and 機能 somewhere.
 */
private static fieldQueryFor(analyzer, String field, String keyword, float boost) {
    def query = new QueryBuilder(analyzer).createPhraseQuery(field, keyword)
    return query ? new BoostQuery(query, boost) : null
}

private static SearchHit toHit(Document doc, query, analyzer, File docDir) {
    def relPath = doc.get(FIELD_PATH)
    def content = doc.get(FIELD_CONTENT) ?: ''
    return new SearchHit(
            file: new File(docDir, relPath),
            relativePath: relPath,
            snippet: bestSnippet(query, analyzer, content))
}

private static String bestSnippet(query, analyzer, String content) {
    if (!content) return ''
    try {
        def highlighter = new Highlighter(new SimpleHTMLFormatter('', ''), new QueryScorer(query))
        highlighter.textFragmenter = new SimpleFragmenter(SNIPPET_CHARS)
        def fragment = highlighter.getBestFragment(analyzer, FIELD_CONTENT, content)
        if (fragment) return fragment.trim()
    } catch (Exception ignored) {
        // Best effort; fall through to a plain preview.
    }
    return content.length() > SNIPPET_CHARS ? content.substring(0, SNIPPET_CHARS) + '…' : content
}

/** [version: int, mtimes: Map]; version is -1 (never a real version) when the file is missing or unreadable. */
private static Map loadMeta(File metaFile) {
    def meta = [version: -1, mtimes: [:]]
    if (!metaFile.isFile()) return meta

    def lines = metaFile.readLines('UTF-8')
    def version = lines ? lines[0] - META_VERSION_PREFIX : ''
    if (!lines || !lines[0].startsWith(META_VERSION_PREFIX) || !version.isInteger()) return meta

    meta.version = version as int
    lines.drop(1).each { line ->
        def (mtime, relPath) = line.split('\t', 2) as List
        if (relPath && mtime.isLong()) meta.mtimes[relPath] = mtime as Long
    }
    return meta
}

private static void saveMeta(File metaFile, Map<String, Long> mtimes) {
    metaFile.withWriter('UTF-8') { writer ->
        writer.writeLine(META_VERSION_PREFIX + INDEX_SCHEMA_VERSION)
        mtimes.each { relPath, mtime -> writer.writeLine("${mtime}\t${relPath}") }
    }
}
