def UNLINKED_NODE_NAME = 'unlinked'

def docDir = Utils.loadDocDir(node)

def linkedFiles = Utils.collectLinkedFiles(node.mindMap.root)

def unlinkedFiles = []
docDir.eachFile { file ->
    if (file.isFile() && file.name.endsWith('.md') && !linkedFiles.contains(file.canonicalFile)) {
        unlinkedFiles << file
    }
}

def rootNode = node.mindMap.root
def unlinkedNode = rootNode.children.find { it.text == UNLINKED_NODE_NAME }
if (!unlinkedNode) {
    unlinkedNode = rootNode.createChild()
    unlinkedNode.text = UNLINKED_NODE_NAME
    unlinkedNode.left = false
} else {
    for (child in unlinkedNode.getChildren()) {
        child.delete()
    }
}

unlinkedFiles.each { file ->
    def childNode = unlinkedNode.createChild()
    childNode.text = file.name.replaceAll(/\.md$/, '')
    childNode.link.file = file
}
