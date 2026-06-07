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

def newNode = toDoNode.createChild(0)
newNode.text = node.text
newNode.link.node = node
c.select(newNode)
