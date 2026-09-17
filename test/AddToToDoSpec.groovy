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
        errorMessages == ['not a todo item: write the draft']
        toDoNode.children.isEmpty()
    }

    def "adds every selected item, keeping the selection order, and selects the new items"() {
        given:
        writePage('Design Memo')
        writePage('Release Plan')
        def existing = addChild(toDoNode, text: 'older task')
        def first = addChild(addPageNode('Design Memo'), text: 'write the draft')
        def second = addChild(addPageNode('Release Plan'), text: 'tag the release')
        selection = [first, second]

        when:
        runScript('AddToToDo.groovy', first)

        then:
        errorMessages.isEmpty()
        toDoNode.children*.text == ['write the draft (Design Memo)', 'tag the release (Release Plan)', 'older task']
        toDoNode.children[1].link.file == new File(docDir, 'Release Plan.md')
        toDoNode.children[2] == existing
        selectedNodes == toDoNode.children[0..1]
    }

    def "adds nothing when a selected node is not an item of a page"() {
        given:
        writePage('Design Memo')
        def item = addChild(addPageNode('Design Memo'), text: 'write the draft')
        def plain = addChild(addChild(rootNode, text: 'plain node'), text: 'not a task')
        selection = [item, plain, rootNode]

        when:
        runScript('AddToToDo.groovy', item)

        then:
        errorMessages == ['not a todo item: not a task, root']
        toDoNode.children.isEmpty()
        selectedNodes.isEmpty()
    }
}
