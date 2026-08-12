class AddAssetsSpec extends ScriptSpec {

    def "creates the assets directory and links it from the top of the page"() {
        given:
        writePage('Design Memo', "# Design Memo\n\n## Contents\n")
        def node = addPageNode('Design Memo')

        when:
        runScript('AddAssets.groovy', node)

        then:
        errorMessages.isEmpty()
        new File(docDir, 'Design Memo.assets').isDirectory()
        readPage('Design Memo') == "[assets](Design Memo.assets)\n\n# Design Memo\n\n## Contents\n"
        hasBom('Design Memo')
    }

    def "keeps a single BOM when the page already has one"() {
        given:
        writePage('Design Memo', BOM_CHAR + "# Design Memo\n")
        def node = addPageNode('Design Memo')

        when:
        runScript('AddAssets.groovy', node)

        then:
        hasBom('Design Memo')
        !readPage('Design Memo').startsWith(BOM_CHAR)
        readPage('Design Memo') == "[assets](Design Memo.assets)\n\n# Design Memo\n"
    }

    def "refuses to add assets twice"() {
        given:
        writePage('Design Memo', "# Design Memo\n")
        makeDir('Design Memo.assets')
        def node = addPageNode('Design Memo')

        when:
        runScript('AddAssets.groovy', node)

        then:
        errorMessages == ['page assets directory already exists.']
        readPage('Design Memo') == "# Design Memo\n"
    }

    def "reports a node that is not a page"() {
        given:
        makeDir('Projects')
        def node = addDirectoryNode('Projects')

        when:
        runScript('AddAssets.groovy', node)

        then:
        errorMessages == ['target node is not a page.']
        !new File(docDir, 'Projects.assets').exists()
    }
}
