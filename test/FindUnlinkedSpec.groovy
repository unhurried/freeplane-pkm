class FindUnlinkedSpec extends ScriptSpec {

    def unlinkedNode() {
        return rootNode.children.find { it.text == 'unlinked' }
    }

    def category(String name) {
        return unlinkedNode().children.find { it.text == name }
    }

    List<String> categoryTexts(String name) {
        return category(name).children.collect { it.text }
    }

    def "sorts document directory entries into pages, directories and assets"() {
        given:
        writePage('Linked Memo')
        writePage('Orphan Memo')
        makeDir('Linked Projects')
        makeDir('Orphan Projects')
        makeDir('Linked Memo.assets')
        makeDir('Orphan Memo.assets')
        makeDir('Gone Memo.assets')
        addPageNode('Linked Memo')
        addDirectoryNode('Linked Projects')

        when:
        runScript('FindUnlinked.groovy', rootNode)

        then:
        categoryTexts('page') == ['Orphan Memo']
        categoryTexts('directory') == ['Orphan Projects']
        categoryTexts('asset') == ['Gone Memo.assets']
    }

    def "links each reported entry to its file"() {
        given:
        writePage('Orphan Memo')
        makeDir('Orphan Projects')

        when:
        runScript('FindUnlinked.groovy', rootNode)

        then:
        category('page').children[0].link.file == new File(docDir, 'Orphan Memo.md')
        category('directory').children[0].link.file == new File(docDir, 'Orphan Projects')
    }

    def "never reports the search index directory"() {
        given:
        makeDir(SearchIndex.INDEX_DIR_NAME)

        when:
        runScript('FindUnlinked.groovy', rootNode)

        then:
        categoryTexts('directory').isEmpty()
        categoryTexts('page').isEmpty()
        categoryTexts('asset').isEmpty()
    }

    def "rebuilds an existing unlinked node without counting its own links"() {
        given:
        writePage('Orphan Memo')
        def existing = addChild(rootNode, text: 'unlinked')
        def staleCategory = addChild(existing, text: 'page')
        addChild(staleCategory, text: 'Gone Memo', linkFile: new File(docDir, 'Orphan Memo.md'))

        when:
        runScript('FindUnlinked.groovy', rootNode)

        then:
        rootNode.children.count { it.text == 'unlinked' } == 1
        unlinkedNode() == existing
        categoryTexts('page') == ['Orphan Memo']
        unlinkedNode().children.collect { it.text } == ['page', 'directory', 'asset']
    }

    def "puts a newly created unlinked node on the right of the map"() {
        when:
        runScript('FindUnlinked.groovy', rootNode)

        then:
        unlinkedNode().left == false
    }
}
