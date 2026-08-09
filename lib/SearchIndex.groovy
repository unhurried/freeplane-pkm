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
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
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
import org.apache.lucene.search.PhraseQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.search.highlight.SimpleFragmenter
import org.apache.lucene.search.highlight.SimpleHTMLFormatter
import org.apache.lucene.store.FSDirectory

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import org.apache.poi.extractor.ExtractorFactory

// Full-text search over the document directory: builds/updates a Lucene index
// (updateIndex) and queries it (search). Content extraction for non-plain-text
// formats is handled here too (extractText), so it can be unit-tested on its
// own without going through the index.
//
// The index lives at <docDir>/INDEX_DIR_NAME/, alongside a small sidecar file
// recording each indexed file's last-modified time; that sidecar - not Lucene
// itself - is what makes updateIndex() incremental (an unmodified file is
// neither re-extracted nor re-added on the next call).

@Field static final String INDEX_DIR_NAME = '.search-index'

@Field private static final String LUCENE_SUBDIR_NAME = 'lucene'
@Field private static final String META_FILE_NAME = 'files.meta'
@Field private static final String VERSION_FILE_NAME = 'index.version'
// Bump whenever the analyzer or field configuration changes in a way that makes
// previously-indexed tokens stop matching newly-indexed ones (e.g. switching the
// Japanese tokenization strategy, as in this version). updateIndex() detects a
// mismatch against the on-disk index.version file and does a full re-index rather
// than an incremental one, so a stale/incompatible index never silently returns
// wrong (or zero) results after an add-on update.
@Field private static final int INDEX_SCHEMA_VERSION = 1

@Field private static final String FIELD_PATH = 'path'
@Field private static final String FIELD_FILENAME = 'filename'
@Field private static final String FIELD_CONTENT = 'content'
// Filename matches are boosted relative to content matches, so a file whose
// name matches a keyword tends to rank above a file that merely mentions it.
@Field private static final Map<String, Float> SEARCH_FIELD_BOOSTS = [(FIELD_FILENAME): 2.0f, (FIELD_CONTENT): 1.0f]

@Field private static final Set<String> TEXT_EXTENSIONS = ['md', 'markdown', 'txt'] as Set
@Field private static final Set<String> PDF_EXTENSIONS = ['pdf'] as Set
@Field private static final Set<String> OFFICE_EXTENSIONS = ['docx', 'xlsx', 'pptx'] as Set

// Defends against a single pathological file (e.g. a huge PDF) blowing up
// index size/memory; personal notes and documents are nowhere near this size.
@Field private static final int MAX_CONTENT_CHARS = 2_000_000
@Field private static final int SNIPPET_CHARS = 160
@Field private static final int DEFAULT_MAX_RESULTS = 100

// Flags for the filename field's WordDelimiterGraphFilter (see
// newJapaneseAnalyzer): split a run of letters/digits on case change
// ("QuarterlyReport" -> "Quarterly"/"Report") and on letter/digit boundaries
// ("Report2024" -> "Report"/"2024"), emitting both the word and number parts.
@Field private static final int FILENAME_SPLIT_FLAGS =
        WordDelimiterGraphFilter.GENERATE_WORD_PARTS | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS |
        WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS

/**
 * Extracts searchable text from a single file, based on its extension:
 * Markdown/plain text is read directly, PDF via PDFBox, and current-format
 * Office documents (docx/xlsx/pptx) via Apache POI. Any other extension - and
 * any file that fails to parse (corrupt, encrypted, unsupported variant, ...)
 * - yields an empty string rather than throwing, so one bad file only costs
 * its own content, not the rest of the scan; the file's name remains
 * searchable regardless.
 *
 * Catches Throwable, not just Exception: PDFBox lazily initializes AWT font
 * mapping on first use (to substitute glyphs for non-embedded fonts), which
 * can throw an Error (e.g. NoClassDefFoundError, wrapping an
 * ExceptionInInitializerError) rather than an Exception when that
 * initialization fails - e.g. under Freeplane's script sandbox, which denies
 * the process-exec permission PDFBox's Windows font-directory lookup wants.
 * Once that happens the affected class stays permanently broken for the rest
 * of the JVM session, so every subsequent file hitting the same code path
 * fails the same way; letting that Error escape here would otherwise crash
 * whatever thread called updateIndex() (see RebuildSearchIndex.groovy and
 * init.groovy), abandoning the whole indexing run rather than just this file.
 */
def static String extractText(File file) {
    def ext = extensionOf(file.name)
    try {
        String text
        if (ext in TEXT_EXTENSIONS) {
            text = readPlainText(file)
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
 * Incrementally builds/updates the full-text index for the document
 * directory: files that are new or modified since the last run (per the
 * sidecar mtime file) are (re-)extracted and (re-)indexed, files that were
 * removed from disk are dropped from the index, and unmodified files are left
 * untouched. Safe to call repeatedly and often (e.g. from a periodic
 * listener) - a fully up-to-date directory does no extraction work at all.
 *
 * If the on-disk index predates the current INDEX_SCHEMA_VERSION (including
 * one built before schema versioning existed at all), every file is instead
 * (re-)extracted and (re-)added regardless of its recorded mtime, and every
 * previously-indexed document is dropped first - so tokens from an
 * incompatible analyzer/field configuration can never linger in, or alongside,
 * the rebuilt index.
 *
 * If the index is already locked by a concurrent update (e.g. the periodic
 * listener and a manual rebuild firing at the same time), this call is a
 * silent no-op: the other update will bring the index up to date instead.
 */
def static void updateIndex(File docDir) {
    def indexDir = new File(docDir, INDEX_DIR_NAME)
    def luceneDir = new File(indexDir, LUCENE_SUBDIR_NAME)
    luceneDir.mkdirs()
    def metaFile = new File(indexDir, META_FILE_NAME)
    def versionFile = new File(indexDir, VERSION_FILE_NAME)
    def storedVersion = readVersion(versionFile)
    def seenPaths = new HashSet<String>()

    FSDirectory.open(luceneDir.toPath()).withCloseable { directory ->
        def config = new IndexWriterConfig(newAnalyzer())
        config.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND

        IndexWriter writer
        try {
            writer = new IndexWriter(directory, config)
        } catch (Exception ignored) {
            return
        }

        // Checked only once the index lock is actually held, so a lock
        // conflict here never discards a still-good meta/version file (see
        // below: on that path this closure returns before either is touched).
        def schemaChanged = storedVersion != INDEX_SCHEMA_VERSION
        def knownMTimes = schemaChanged ? [:] : loadMeta(metaFile)

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

        // Only reached once the writer above has committed successfully, so a
        // failed/interrupted rebuild is retried in full on the next call
        // instead of being (incorrectly) marked as done.
        saveMeta(metaFile, knownMTimes)
        if (schemaChanged) versionFile.text = INDEX_SCHEMA_VERSION as String
    }
}

/**
 * Searches the document directory's index for files matching every given
 * keyword (AND, case-insensitive), against both file content and file name.
 * Keywords are whitespace-separated and matched as literal text - not query
 * syntax, so a keyword containing e.g. ":" or "(" is searched for as-is
 * rather than rejected or misinterpreted. Returns an empty list if the index
 * does not exist yet (updateIndex has never run), the query has no keywords,
 * or no keyword tokenizes to anything searchable (e.g. punctuation only).
 */
def static List<SearchHit> search(File docDir, String queryText, int maxResults = DEFAULT_MAX_RESULTS) {
    def keywords = queryText?.trim() ? queryText.trim().split(/\s+/) as List : []
    if (!keywords) return []

    def luceneDir = new File(new File(docDir, INDEX_DIR_NAME), LUCENE_SUBDIR_NAME)
    if (!luceneDir.exists()) return []

    def analyzer = newAnalyzer()
    def query = andQuery(analyzer, keywords)
    if (!query) return []

    return FSDirectory.open(luceneDir.toPath()).withCloseable { directory ->
        DirectoryReader.open(directory).withCloseable { reader ->
            def searcher = new IndexSearcher(reader)
            searcher.search(query, maxResults).scoreDocs.collect { scoreDoc ->
                toHit(searcher.doc(scoreDoc.doc), query, analyzer, docDir)
            }
        }
    }
}

// --- Private helper methods ---

private static newAnalyzer() {
    // JapaneseTokenizer (Kuromoji) does real morphological analysis - using its
    // bundled IPADIC dictionary to split text into actual words and normalize
    // inflected forms to their dictionary (base) form - rather than the
    // mechanical bigram splitting of a CJK bigram analyzer. That avoids two
    // classes of bad result: false matches from bigrams that happen to
    // straddle unrelated words (searching "京都" ("Kyoto") would otherwise
    // also match "東京都" ("Tokyo"), which merely contains the same two
    // characters in sequence), and missed matches on inflected forms (a
    // search for the dictionary form "読む" ("read") would otherwise not
    // find "読んだ" ("read", past tense)). SEARCH mode additionally splits
    // long compound nouns into their parts (e.g. "関西国際空港" ->
    // "関西"/"国際"/"空港"), so a search for one part still matches; ordinary
    // (non-CJK) text is tokenized word-by-word same as before, and lowercased
    // for case-insensitivity. The empty stop-word set keeps every typed
    // keyword significant instead of silently dropping common words.
    //
    // The filename field uses the same analyzer with one addition (see
    // newJapaneseAnalyzer): it also splits on case/digit boundaries, so e.g.
    // "QuarterlyReport" is searchable as "Quarterly" alone.
    def contentAnalyzer = newJapaneseAnalyzer(false)
    return new PerFieldAnalyzerWrapper(contentAnalyzer, [(FIELD_FILENAME): newJapaneseAnalyzer(true)])
}

private static Analyzer newJapaneseAnalyzer(boolean forFilename) {
    return new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            // discardPunctuation drops "." "_" "-" spaces etc. as hard token
            // breaks, so (for the filename field) "Report.md" tokenizes as
            // "Report"/"md" rather than one token, letting a search for
            // "Report" alone match. discardCompoundToken suppresses the
            // original, undivided compound token that SEARCH mode would
            // otherwise additionally emit at the same position as its parts;
            // keeping only the split parts gives a flat token sequence, which
            // is what fieldQueryFor()'s PhraseQuery construction (built from
            // consecutive token positions) assumes.
            def tokenizer = new JapaneseTokenizer(null, true, true, JapaneseTokenizer.Mode.SEARCH)
            TokenStream stream = new JapaneseBaseFormFilter(tokenizer) // "読んだ" -> "読む"
            stream = new CJKWidthFilter(stream)                       // normalize full/half-width forms
            stream = new JapaneseKatakanaStemFilter(stream)           // "コンピューター" -> "コンピュータ"
            if (forFilename) {
                // Further splits "QuarterlyReport" into "Quarterly"/"Report"
                // (case change) and "Report2024" into "Report"/"2024"
                // (letter/digit change); must run before lowercasing, since
                // it splits on case.
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

private static String readPlainText(File file) {
    def text = file.getText('UTF-8')
    return text.startsWith('﻿') ? text.substring(1) : text
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

/**
 * Recursively visits every regular file under docDir, calling
 * action(file, relativePath) for each. Skips the search index directory
 * itself and any other dot-directory (e.g. a stray .git), so the scan never
 * indexes its own index or unrelated tooling state.
 */
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

/**
 * Builds a query requiring every keyword to match (AND), where a single
 * keyword matches if either the filename or content field contains it
 * (OR, weighted by SEARCH_FIELD_BOOSTS). Built directly from analyzer tokens
 * rather than through a query-string parser, so arbitrary user input (Lucene
 * operators, punctuation, ...) is always treated as literal search text and
 * can never fail to parse or be misread as query syntax.
 */
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

private static fieldQueryFor(analyzer, String field, String keyword, float boost) {
    def tokens = tokensOf(analyzer, field, keyword)
    if (!tokens) return null
    if (tokens.size() == 1) return new BoostQuery(new TermQuery(new Term(field, tokens[0].text)), boost)

    // A keyword that tokenizes to several tokens - CJK text split into
    // multiple words by JapaneseTokenizer, or a filename fragment split on a
    // case/digit boundary by WordDelimiterGraphFilter - is required to match
    // as a contiguous phrase, at the same relative positions the analyzer
    // produced, so e.g. "検索機能" ("search function") matches "検索機能" but
    // not unrelated text that merely contains both of its words somewhere
    // else in the field.
    def phrase = new PhraseQuery.Builder()
    tokens.each { token -> phrase.add(new Term(field, token.text), token.position) }
    return new BoostQuery(phrase.build(), boost)
}

/**
 * Runs text through the given field's analyzer, returning each token's text
 * together with its position - accumulated from PositionIncrementAttribute
 * rather than assumed to advance by exactly one per token, since that does
 * not hold for every analyzer/filter (e.g. a synonym-like filter could emit
 * more than one token at the same position). Positions are what
 * fieldQueryFor() needs to build a phrase query that only matches the
 * analyzer's actual token layout.
 */
private static List<Map> tokensOf(analyzer, String field, String text) {
    def tokens = []
    def tokenStream = analyzer.tokenStream(field, text)
    def termAttr = tokenStream.addAttribute(CharTermAttribute)
    def posAttr = tokenStream.addAttribute(PositionIncrementAttribute)
    tokenStream.reset()
    def position = -1
    while (tokenStream.incrementToken()) {
        position += posAttr.positionIncrement
        tokens << [text: termAttr.toString(), position: position]
    }
    tokenStream.end()
    tokenStream.close()
    return tokens
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
        // Highlighting is best-effort; fall through to a plain preview below.
    }
    return content.length() > SNIPPET_CHARS ? content.substring(0, SNIPPET_CHARS) + '…' : content
}

private static Map<String, Long> loadMeta(File metaFile) {
    def mtimes = [:]
    if (!metaFile.exists()) return mtimes

    def props = new Properties()
    metaFile.withReader('UTF-8') { reader -> props.load(reader) }
    props.each { key, value ->
        try {
            mtimes[key] = Long.parseLong(value)
        } catch (NumberFormatException ignored) {
            // Corrupt entry - treat the file as unknown, so it gets re-indexed.
        }
    }
    return mtimes
}

private static void saveMeta(File metaFile, Map<String, Long> mtimes) {
    def props = new Properties()
    mtimes.each { relPath, mtime -> props.setProperty(relPath, mtime as String) }
    metaFile.withWriter('UTF-8') { writer -> props.store(writer, null) }
}

// -1 never matches a real INDEX_SCHEMA_VERSION (versions start at 1), so both
// a missing file (no version has ever been recorded - e.g. an index built
// before schema versioning existed, or before this dictionary-based Japanese
// analyzer replaced the earlier bigram one) and a corrupt one are treated the
// same as any other mismatch: trigger a full rebuild.
private static int readVersion(File versionFile) {
    if (!versionFile.exists()) return -1
    try {
        return Integer.parseInt(versionFile.text.trim())
    } catch (Exception ignored) {
        return -1
    }
}
