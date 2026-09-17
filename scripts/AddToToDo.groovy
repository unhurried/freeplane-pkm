// @ExecutionModes({ON_SINGLE_NODE})
// Copies the selected items (children of page nodes) to the top of root > ToDo,
// each linked back to its page, keeping their order; nothing is copied while
// any selected node is not an item of a page.
def toDoNode = Utils.findChildByText(node.mindMap.root, Utils.TODO_NODE_NAME)
if (!toDoNode) {
    ui.errorMessage('ToDo node is missing.')
    return
}

def items = c.selecteds
def docDir = Utils.loadDocDir(node)
def others = items.findAll { it.parent == null || Utils.getDocNodeType(it.parent, docDir) != Utils.DOC_TARGET_PAGE }
if (others) {
    ui.errorMessage("not a todo item: ${others*.text.join(', ')}")
    return
}

def newNodes = items.withIndex().collect { item, i ->
    def newNode = toDoNode.createChild(i)
    newNode.text = Utils.toDoItemText(item.text, item.parent.text)
    newNode.link.file = Utils.getLinkedFile(item.parent)
    return newNode
}
c.select(newNodes)
