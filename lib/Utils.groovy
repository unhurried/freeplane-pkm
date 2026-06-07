import groovy.transform.Field

@Field static final DOC_TARGET_PAGE = 'page'
@Field static final DOC_TARGET_DIRECTORY = 'directory'

@Field private static final NEXT_STEPS_HEADING = '### Next Steps'
@Field private static final MAX_NEXT_STEPS = 3
@Field private static final CONFIG_NODE_NAME = 'config'
@Field private static final DOC_DIR_PATH_KEYS = ['docDirPath', 'pageDirPath']

/**
 * Loads the document directory path from the mind map's config node.
 * Config structure: root > config > docDirPath > [path value]
 */
def static loadDocDir(node) {
    def configNode = findChildByText(node.mindMap.root, CONFIG_NODE_NAME)
    if (!configNode) throw new RuntimeException('config node is missing.')

    def docDirPath = loadDocDirPath(configNode)
    if (!docDirPath) throw new RuntimeException('docDirPath node is missing.')

    def docDir = new File(docDirPath)
    if (!docDir.exists()) throw new RuntimeException('document directory is missing.')

    return docDir
}

/**
 * Returns the file linked from the node (directly or via an intermediate node link).
 */
def static getLinkedFile(node) {
    if (node.link.node && node.link.node.link.file) {
        return node.link.node.link.file
    }
    return node.link.file ?: null
}

/**
 * Determines whether the node represents a page or directory document.
 * Returns DOC_TARGET_PAGE, DOC_TARGET_DIRECTORY, or null.
 */
def static getDocNodeType(node) {
    def linkedFile = Utils.getLinkedFile(node)
    if (!linkedFile?.exists()) return null

    def docDir = Utils.loadDocDir(node)
    def docName = node.text

    if (isPageNode(linkedFile, docDir, docName)) {
        return Utils.DOC_TARGET_PAGE
    }
    if (isDirectoryNode(linkedFile, docDir, docName)) {
        return Utils.DOC_TARGET_DIRECTORY
    }

    return null
}

/**
 * Returns the page file (.md) linked from the node, or null if not applicable.
 * Throws RuntimeException if the node links to a page file that doesn't exist.
 */
def static getPageFile(node) {
    def pageFile = Utils.getLinkedFile(node)
    if (!pageFile) return null

    def docDir = Utils.loadDocDir(node)
    def expectedFile = new File(docDir, node.text + '.md')
    if (pageFile != expectedFile) return null

    if (!pageFile.exists()) throw new RuntimeException('page file is missing.')

    return pageFile
}

/**
 * Reads the "### Next Steps" section from the page file and updates
 * the node's children with up to MAX_NEXT_STEPS items.
 */
def static updateNextSteps(node) {
    def pageFile = Utils.getPageFile(node)
    if (!pageFile) return

    pageFile.withReader { reader ->
        skipToNextStepsHeading(reader)
        clearChildren(node)
        addNextStepsAsChildren(reader, node)
    }
}

// --- Private helper methods ---

private static findChildByText(parentNode, String text) {
    return parentNode.children.find { it.text == text }
}

private static String loadDocDirPath(configNode) {
    for (child in configNode.children) {
        if (child.text in DOC_DIR_PATH_KEYS && child.children[0]) {
            return child.children[0].plainText
        }
    }
    return null
}

private static boolean isPageNode(File linkedFile, File docDir, String docName) {
    def pageFile = new File(docDir, docName + '.md')
    return linkedFile == pageFile && pageFile.exists()
}

private static boolean isDirectoryNode(File linkedFile, File docDir, String docName) {
    def directoryDir = new File(docDir, docName)
    return linkedFile == directoryDir && directoryDir.exists() && directoryDir.isDirectory()
}

private static void skipToNextStepsHeading(Reader reader) {
    while (true) {
        String line = reader.readLine()
        if (line == null || line == NEXT_STEPS_HEADING) break
    }
}

private static void clearChildren(node) {
    for (child in node.getChildren()) {
        child.delete()
    }
}

private static void addNextStepsAsChildren(Reader reader, node) {
    int count = 0
    while (true) {
        String line = reader.readLine()
        if (line == null || line.startsWith('#')) break

        if (isListItem(line)) {
            def newNode = node.createChild()
            newNode.text = line.substring(2)
            count++
            if (count == MAX_NEXT_STEPS) break
        }
    }
}

private static boolean isListItem(String line) {
    return (line.startsWith('* ') || line.startsWith('- ')) && line.length() > 3
}
