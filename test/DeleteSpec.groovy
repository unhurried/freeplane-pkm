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
        errorMessages == ['target node is neither page nor directory.']
        !node.deleted
    }
}
