def UNLINKED_NODE_NAME = 'unlinked assets'

def docDir = Utils.loadDocDir(node)

def unlinkedAssetDirs = []
docDir.eachFile { file ->
    if (file.isDirectory() && file.name.endsWith('.assets')) {
        def baseName = file.name.replaceAll(/\.assets$/, '')
        def pageFile = new File(docDir, baseName + '.md')
        if (!pageFile.exists()) {
            unlinkedAssetDirs << file
        }
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

unlinkedAssetDirs.each { dir ->
    def childNode = unlinkedNode.createChild()
    childNode.text = dir.name
    childNode.link.file = dir
}
