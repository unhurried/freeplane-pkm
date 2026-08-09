import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.xwpf.usermodel.XWPFDocument
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SearchIndexSpec extends Specification {

    @TempDir
    Path tempDir

    File docDir

    def setup() {
        docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
    }

    // --- Helpers to create fixture files of each supported format ---

    private File textFile(String name, String content) {
        def file = new File(docDir, name)
        file.text = content
        return file
    }

    private File pdfFile(String name, String text) {
        def file = new File(docDir, name)
        def doc = new PDDocument()
        try {
            def page = new PDPage()
            doc.addPage(page)
            def cs = new PDPageContentStream(doc, page)
            try {
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12)
                cs.newLineAtOffset(50, 700)
                cs.showText(text)
                cs.endText()
            } finally {
                cs.close()
            }
            doc.save(file)
        } finally {
            doc.close()
        }
        return file
    }

    private File docxFile(String name, String text) {
        def file = new File(docDir, name)
        def doc = new XWPFDocument()
        try {
            doc.createParagraph().createRun().setText(text)
            file.withOutputStream { doc.write(it) }
        } finally {
            doc.close()
        }
        return file
    }

    // --- Tests for extractText ---

    def "extractText reads Markdown files directly"() {
        given:
        def file = textFile('Note.md', '# Heading\n\nSome content here.')

        expect:
        SearchIndex.extractText(file) == '# Heading\n\nSome content here.'
    }

    def "extractText strips a leading byte-order mark"() {
        given:
        def file = new File(docDir, 'Note.md')
        file.bytes = ([(byte) 0xEF, (byte) 0xBB, (byte) 0xBF] as byte[])
        file.append('Hello', 'UTF-8')

        expect:
        SearchIndex.extractText(file) == 'Hello'
    }

    def "extractText extracts text from a PDF"() {
        given:
        def file = pdfFile('Doc.pdf', 'Hello from PDF')

        expect:
        SearchIndex.extractText(file).contains('Hello from PDF')
    }

    def "extractText extracts text from a docx"() {
        given:
        def file = docxFile('Doc.docx', 'Hello from Word')

        expect:
        SearchIndex.extractText(file).contains('Hello from Word')
    }

    def "extractText returns empty string for unsupported extensions"() {
        given:
        def file = textFile('image.png', 'not really an image')

        expect:
        SearchIndex.extractText(file) == ''
    }

    def "extractText returns empty string instead of throwing for a corrupt file"() {
        given:
        def file = textFile('Broken.pdf', 'this is not a valid pdf')

        expect:
        SearchIndex.extractText(file) == ''
    }

    // --- Tests for updateIndex + search ---

    def "search finds a file by content keyword"() {
        given:
        textFile('Alpha.md', 'This page talks about mountains and rivers.')
        textFile('Beta.md', 'This page talks about oceans.')
        SearchIndex.updateIndex(docDir)

        when:
        def hits = SearchIndex.search(docDir, 'mountains')

        then:
        hits.size() == 1
        hits[0].relativePath == 'Alpha.md'
    }

    def "search is case-insensitive"() {
        given:
        textFile('Alpha.md', 'This page talks about Mountains.')
        SearchIndex.updateIndex(docDir)

        expect:
        SearchIndex.search(docDir, 'MOUNTAINS').size() == 1
        SearchIndex.search(docDir, 'mountains').size() == 1
    }

    def "search combines multiple keywords with AND"() {
        given:
        textFile('Alpha.md', 'mountains and rivers')
        textFile('Beta.md', 'mountains and oceans')
        SearchIndex.updateIndex(docDir)

        when:
        def hits = SearchIndex.search(docDir, 'mountains rivers')

        then:
        hits.size() == 1
        hits[0].relativePath == 'Alpha.md'
    }

    def "search matches file names as well as content"() {
        given:
        textFile('QuarterlyReport.md', 'nothing relevant in here')
        SearchIndex.updateIndex(docDir)

        when:
        def hits = SearchIndex.search(docDir, 'Quarterly')

        then:
        hits.size() == 1
        hits[0].relativePath == 'QuarterlyReport.md'
    }

    def "search finds files of unsupported types by name even without content"() {
        given:
        textFile('diagram.xyz', 'binary-ish content SearchIndex cannot parse')
        SearchIndex.updateIndex(docDir)

        when:
        def hits = SearchIndex.search(docDir, 'diagram')

        then:
        hits.size() == 1
        hits[0].snippet == ''
    }

    def "search returns a snippet for content matches"() {
        given:
        textFile('Alpha.md', 'Some introduction. This page talks about mountains and rivers. A conclusion.')
        SearchIndex.updateIndex(docDir)

        when:
        def hits = SearchIndex.search(docDir, 'mountains')

        then:
        hits[0].snippet.contains('mountains')
    }

    def "search returns no results for an unindexed document directory"() {
        expect:
        SearchIndex.search(docDir, 'anything') == []
    }

    def "search returns no results for a blank query"() {
        given:
        textFile('Alpha.md', 'content')
        SearchIndex.updateIndex(docDir)

        expect:
        SearchIndex.search(docDir, '   ') == []
    }

    def "search treats special characters in keywords literally instead of throwing"() {
        given:
        textFile('Alpha.md', 'cost: (approx) 100')
        SearchIndex.updateIndex(docDir)

        expect:
        SearchIndex.search(docDir, 'AND OR NOT :(*?~^]') == []
    }

    def "updateIndex picks up content changes on re-run"() {
        given:
        def file = textFile('Alpha.md', 'first version')
        file.setLastModified(1000L)
        SearchIndex.updateIndex(docDir)

        when:
        file.text = 'second version'
        file.setLastModified(2000L)
        SearchIndex.updateIndex(docDir)

        then:
        SearchIndex.search(docDir, 'first') == []
        SearchIndex.search(docDir, 'second').size() == 1
    }

    def "updateIndex removes entries for files deleted from disk"() {
        given:
        def file = textFile('Alpha.md', 'mountains')
        SearchIndex.updateIndex(docDir)
        assert SearchIndex.search(docDir, 'mountains').size() == 1

        when:
        file.delete()
        SearchIndex.updateIndex(docDir)

        then:
        SearchIndex.search(docDir, 'mountains') == []
    }

    def "updateIndex does not index its own search index directory"() {
        given:
        textFile('Alpha.md', 'mountains')
        SearchIndex.updateIndex(docDir)
        SearchIndex.updateIndex(docDir)

        expect:
        SearchIndex.search(docDir, SearchIndex.INDEX_DIR_NAME) == []
    }

    def "updateIndex is safe to call on an empty document directory"() {
        when:
        SearchIndex.updateIndex(docDir)

        then:
        noExceptionThrown()
        SearchIndex.search(docDir, 'anything') == []
    }
}
