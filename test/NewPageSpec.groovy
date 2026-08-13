class NewPageSpec extends ScriptSpec {

    def "creates the page from the template, its assets directory, and links the node"() {
        given:
        def node = addChild(rootNode, text: 'Design Memo')

        when:
        runScript('NewPage.groovy', node)

        then:
        errorMessages.isEmpty()
        new File(docDir, 'Design Memo.md').isFile()
        new File(docDir, 'Design Memo.assets').isDirectory()
        node.link.file == new File(docDir, 'Design Memo.md')
        openedInDesktop == [new File(docDir, 'Design Memo.md')]
    }

    def "substitutes the assets directory name and today's date into the template"() {
        given:
        def node = addChild(rootNode, text: 'Design Memo')

        when:
        runScript('NewPage.groovy', node)

        then:
        def text = readPage('Design Memo')
        text.contains('[assets](Design Memo.assets)')
        text.contains('### Next Steps')
        text.contains(new Date().format('yy/MM/dd'))
        !text.contains('${')
        // Exactly one BOM: the script writes its own, and the template's is not carried over.
        hasBom('Design Memo')
        !text.startsWith(BOM_CHAR)
    }

    def "rejects a page name containing file system reserved characters"() {
        given:
        def node = addChild(rootNode, text: 'Design/Memo')

        when:
        runScript('NewPage.groovy', node)

        then:
        errorMessages == ['page name includes invalid characters']
        docDir.listFiles().length == 0
        openedInDesktop.isEmpty()
    }

    def "links the node to an existing page instead of overwriting it"() {
        given:
        writePage('Design Memo', 'original content\n')
        def node = addChild(rootNode, text: 'Design Memo')

        when:
        runScript('NewPage.groovy', node)

        then:
        errorMessages == ['page file already exists.']
        readPage('Design Memo') == 'original content\n'
        node.link.file == new File(docDir, 'Design Memo.md')
        openedInDesktop.isEmpty()
    }

    def "refuses to create a page whose assets directory already exists"() {
        given:
        makeDir('Design Memo.assets')
        def node = addChild(rootNode, text: 'Design Memo')

        when:
        runScript('NewPage.groovy', node)

        then:
        errorMessages == ['page assets directory already exists.']
        !new File(docDir, 'Design Memo.md').exists()
    }

    def "reports a missing template file"() {
        given:
        userDirectory = tempDir.resolve('empty-user-dir').toFile()
        userDirectory.mkdirs()
        def node = addChild(rootNode, text: 'Design Memo')

        when:
        runScript('NewPage.groovy', node)

        then:
        errorMessages == ['template file is missing.']
        docDir.listFiles().length == 0
    }
}
