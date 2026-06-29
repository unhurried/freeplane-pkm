def UNLINKED_NODE_NAME = 'unlinked pages / directories'

def docDir = Utils.loadDocDir(node)

def linkedFiles = Utils.collectLinkedFiles(node.mindMap.root)

def unlinkedFiles = []
def unlinkedDirs = []
docDir.eachFile { file ->
    if (file.isFile() && file.name.endsWith('.md') && !linkedFiles.contains(file.canonicalFile)) {
        unlinkedFiles << file
    }
    if (file.isDirectory() && !file.name.endsWith('.assets') && !linkedFiles.contains(file.canonicalFile)) {
        unlinkedDirs << file
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

unlinkedDirs.each { dir ->
    def childNode = unlinkedNode.createChild()
    childNode.text = dir.name
    childNode.link.file = dir
}
