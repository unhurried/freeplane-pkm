def ToDoNode
for (child in c.getViewRoot().children) {
    if (child.text == 'ToDo') {
        ToDoNode = child
        break
    }
}

if (!ToDoNode) {
    ui.errorMessage('ToDo node is missing.')
    return
}
 
def newNode = ToDoNode.createChild()
newNode.text = node.text
newNode.link.node = node
c.select(newNode)
