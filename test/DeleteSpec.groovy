import javax.swing.JOptionPane

class DeleteSpec extends ScriptSpec {

    def "deletes the page, its assets directory and the node once confirmed"() {
        given:
        writePage('Design Memo')
        makeDir('Design Memo.assets')
        new File(docDir, 'Design Memo.assets/diagram.png').setText('x')
        def node = addPageNode('Design Memo')

        when:
        runScript('Delete.groovy', node)

        then:
        errorMessages.isEmpty()
        !new File(docDir, 'Design Memo.md').exists()
        !new File(docDir, 'Design Memo.assets').exists()
        node.deleted
        !rootNode.children.contains(node)
    }

    def "keeps everything when the confirmation is declined"() {
        given:
        writePage('Design Memo')
        makeDir('Design Memo.assets')
        def node = addPageNode('Design Memo')
        confirmAnswer = JOptionPane.NO_OPTION

        when:
        runScript('Delete.groovy', node)

        then:
        errorMessages.isEmpty()
        new File(docDir, 'Design Memo.md').isFile()
        new File(docDir, 'Design Memo.assets').isDirectory()
        !node.deleted
    }

    def "deletes a directory node with its contents"() {
        given:
        makeDir('Projects')
        makeDir('Projects/nested')
        new File(docDir, 'Projects/nested/note.md').setText('x')
        def node = addDirectoryNode('Projects')

        when:
        runScript('Delete.groovy', node)

        then:
        errorMessages.isEmpty()
        !new File(docDir, 'Projects').exists()
        node.deleted
    }

    def "reports a node that is neither a page nor a directory"() {
        given:
        def node = addPageNode('Missing Memo')

        when:
        runScript('Delete.groovy', node)

        then:
        errorMessages == ['not a page or directory: Missing Memo']
        !node.deleted
    }

    def "deletes every selected page and directory after a single confirmation listing them"() {
        given:
        writePage('Design Memo')
        makeDir('Design Memo.assets')
        makeDir('Projects')
        def page = addPageNode('Design Memo')
        def directory = addDirectoryNode('Projects')
        selection = [page, directory]

        when:
        runScript('Delete.groovy', page)

        then:
        errorMessages.isEmpty()
        confirmQuestions == ['Are you sure to delete the following?\n"Design Memo"\ndirectory "Projects"']
        !new File(docDir, 'Design Memo.md').exists()
        !new File(docDir, 'Design Memo.assets').exists()
        !new File(docDir, 'Projects').exists()
        page.deleted
        directory.deleted
    }

    def "deletes nothing when a selected node is neither a page nor a directory"() {
        given:
        writePage('Design Memo')
        def page = addPageNode('Design Memo')
        def plain = addChild(rootNode, text: 'plain node')
        def missing = addPageNode('Missing Memo')
        selection = [page, plain, missing]

        when:
        runScript('Delete.groovy', page)

        then:
        errorMessages == ['not a page or directory: plain node, Missing Memo']
        confirmQuestions.isEmpty()
        new File(docDir, 'Design Memo.md').isFile()
        !page.deleted
    }

    // Deleting the directory removes the page's file along with it; the page node
    // goes with the directory node's subtree, so it must not be deleted a second time.
    def "deletes a selected page under a selected directory only through the directory"() {
        given:
        makeDir('Projects')
        new File(docDir, 'Projects/Design Memo.md').setText('x')
        def directory = addDirectoryNode('Projects')
        def page = addChild([text: 'Design Memo', linkFile: new File(docDir, 'Projects/Design Memo.md')], directory)
        selection = [directory, page]

        when:
        runScript('Delete.groovy', directory)

        then:
        errorMessages.isEmpty()
        confirmQuestions == ['Are you sure to delete directory "Projects"?']
        !new File(docDir, 'Projects').exists()
        directory.deleted
        !page.deleted
        directory.children.contains(page)
    }
}
