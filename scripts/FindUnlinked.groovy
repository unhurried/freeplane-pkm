// @ExecutionModes({ON_SINGLE_NODE})
def UNLINKED_NODE_NAME = 'unlinked'

def docDir = Utils.loadDocDir(node)

// Prepare the unlinked node before collecting linked files, so that links held by
// the previous unlinked node's children are not counted as "linked".
def rootNode = node.mindMap.root
def unlinkedNode = Utils.findChildByText(rootNode, UNLINKED_NODE_NAME)
if (!unlinkedNode) {
    unlinkedNode = rootNode.createChild()
    unlinkedNode.text = UNLINKED_NODE_NAME
    unlinkedNode.left = false
} else {
    Utils.deleteChildren(unlinkedNode)
}

def linkedFiles = Utils.collectNodesByLinkedFile(rootNode).keySet()
def isLinked = { File file -> linkedFiles.contains(Utils.normalizedFile(file)) }

def unlinkedPages = []
def unlinkedDirs = []
def unlinkedAssets = []
docDir.eachFile { file ->
    // The search index is maintenance data, not a document - never report it.
    if (file.name == SearchIndex.INDEX_DIR_NAME) return

    def pageFileOfAssets = Utils.pageFileOfAssetsDir(file)
    if (file.isFile()) {
        if (Utils.pageNameOf(file) != null && !isLinked(file)) unlinkedPages << file
    } else if (pageFileOfAssets) {
        if (!pageFileOfAssets.exists()) unlinkedAssets << file
    } else if (!isLinked(file)) {
        unlinkedDirs << file
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

addCategory('page', unlinkedPages) { Utils.pageNameOf(it) }
addCategory('directory', unlinkedDirs) { it.name }
addCategory('asset', unlinkedAssets) { it.name }
