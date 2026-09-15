// Copies a page's child item to the top of root > ToDo, linked back to the page.
def toDoNode = Utils.findChildByText(node.mindMap.root, Utils.TODO_NODE_NAME)
if (!toDoNode) {
    ui.errorMessage('ToDo node is missing.')
    return
}

if (Utils.getDocNodeType(node.parent) != Utils.DOC_TARGET_PAGE) {
    ui.errorMessage('This is not a todo item.')
    return
}

def newNode = toDoNode.createChild(0)
newNode.text = Utils.toDoItemText(node.text, node.parent.text)
newNode.link.file = Utils.getLinkedFile(node.parent)
c.select(newNode)
