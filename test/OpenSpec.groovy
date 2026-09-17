class OpenSpec extends ScriptSpec {

    def "opens the page file for a page node"() {
        given:
        writePage('Design Memo')
        def node = addPageNode('Design Memo')

        when:
        runScript('Open.groovy', node)

        then:
        errorMessages.isEmpty()
        openedInDesktop == [new File(docDir, 'Design Memo.md')]
    }

    def "opens the directory for a directory node"() {
        given:
        makeDir('Projects')
        def node = addDirectoryNode('Projects')

        when:
        runScript('Open.groovy', node)

        then:
        errorMessages.isEmpty()
        openedInDesktop == [new File(docDir, 'Projects')]
    }

    def "does nothing for a node that is neither a page nor a directory"() {
        given:
        def node = addPageNode('Missing Memo')

        when:
        runScript('Open.groovy', node)

        then:
        errorMessages.isEmpty()
        openedInDesktop.isEmpty()
    }

    def "opens every page and directory in the selection, skipping other nodes"() {
        given:
        writePage('Design Memo')
        makeDir('Projects')
        def page = addPageNode('Design Memo')
        def directory = addDirectoryNode('Projects')
        def plain = addChild(rootNode, text: 'plain node')
        selection = [page, plain, directory]

        when:
        runScript('Open.groovy', page)

        then:
        errorMessages.isEmpty()
        openedInDesktop == [new File(docDir, 'Design Memo.md'), new File(docDir, 'Projects')]
    }
}
