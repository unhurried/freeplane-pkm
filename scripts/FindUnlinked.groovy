def UNLINKED_NODE_NAME = 'unlinked'

def docDir = Utils.loadDocDir(node)

def linkedFiles = Utils.collectLinkedFiles(node.mindMap.root)

def unlinkedPages = []
def unlinkedDirs = []
def unlinkedAssets = []
docDir.eachFile { file ->
    if (file.isFile() && file.name.endsWith('.md') && !linkedFiles.contains(file.canonicalFile)) {
        unlinkedPages << file
    } else if (file.isDirectory() && file.name.endsWith('.assets')) {
        def baseName = file.name.replaceAll(/\.assets$/, '')
        def pageFile = new File(docDir, baseName + '.md')
        if (!pageFile.exists()) {
            unlinkedAssets << file
        }
    } else if (file.isDirectory() && !linkedFiles.contains(file.canonicalFile)) {
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

def addCategory = { String label, List items, Closure nameOf ->
    def catNode = unlinkedNode.createChild()
    catNode.text = label
    items.each { item ->
        def childNode = catNode.createChild()
        childNode.text = nameOf(item)
        childNode.link.file = item
    }
}

addCategory('page', unlinkedPages) { it.name.replaceAll(/\.md$/, '') }
addCategory('directory', unlinkedDirs) { it.name }
addCategory('asset', unlinkedAssets) { it.name }
