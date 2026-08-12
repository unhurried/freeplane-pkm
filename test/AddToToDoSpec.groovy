class AddToToDoSpec extends ScriptSpec {

    def toDoNode

    def setup() {
        toDoNode = addChild(rootNode, text: 'ToDo')
    }

    def "adds the item at the top of the ToDo node, tagged with its page"() {
        given:
        writePage('Design Memo')
        def pageNode = addPageNode('Design Memo')
        def existing = addChild(toDoNode, text: 'older task')
        def node = addChild(pageNode, text: 'write the draft')

        when:
        runScript('AddToToDo.groovy', node)

        then:
        errorMessages.isEmpty()
        toDoNode.children.size() == 2
        toDoNode.children[0].text == 'write the draft (Design Memo)'
        toDoNode.children[0].link.file == new File(docDir, 'Design Memo.md')
        toDoNode.children[1] == existing
        selectedNodes == [toDoNode.children[0]]
    }

    def "reports a missing ToDo node"() {
        given:
        rootNode.removeChild(toDoNode)
        writePage('Design Memo')
        def node = addChild(addPageNode('Design Memo'), text: 'write the draft')

        when:
        runScript('AddToToDo.groovy', node)

        then:
        errorMessages == ['ToDo node is missing.']
        selectedNodes.isEmpty()
    }

    def "reports an item whose parent is not a page"() {
        given:
        def node = addChild(addChild(rootNode, text: 'plain node'), text: 'write the draft')

        when:
        runScript('AddToToDo.groovy', node)

        then:
        errorMessages == ['This is not a todo item.']
        toDoNode.children.isEmpty()
    }
}
