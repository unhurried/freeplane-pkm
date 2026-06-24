def toDoNode = null
for (child in c.getViewRoot().children) {
    if (child.text == 'ToDo') {
        toDoNode = child
        break
    }
}

if (!toDoNode) {
    ui.errorMessage('ToDo node is missing.')
    return
}

// 親NodeがPage Nodeであることを確認する。
if (Utils.getDocNodeType(node.parent) != Utils.DOC_TARGET_PAGE) {
    ui.errorMessage('This is not a todo item.')
    return
}

def newNode = toDoNode.createChild(0)
newNode.text = node.text + " (" + node.parent.text + ")"
newNode.link.file = node.parent.link.file
c.select(newNode)
