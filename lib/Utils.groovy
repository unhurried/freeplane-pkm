import groovy.transform.Field

@Field static final DOC_TARGET_PAGE = 'page'
@Field static final DOC_TARGET_DIRECTORY = 'directory'

def static loadDocDir(node) {
    def configNode
    for (child in node.mindMap.root.children) {
        if (child.text == 'config') {
            configNode = child
            break
        }
    }
    if (!configNode) throw new RuntimeException('config node is missing.')

    def docDirPath
    for (child in configNode.children) {
        if ((child.text == 'docDirPath' || child.text == 'pageDirPath') && child.children[0]) {
            docDirPath = child.children[0].plainText
        }
    }
    if (!docDirPath) throw new RuntimeException('docDirPath node is missing.')

    def docDir = new File(docDirPath)
    if (!docDir.exists()) throw new RuntimeException('document directory is missing.')

    return docDir
}

def static getLinkedFile(node) {
    if (node.link.node && node.link.node.link.file) {
        return node.link.node.link.file
    }
    if (node.link.file) {
        return node.link.file
    }

    return null
}

def static getDocNodeType(node) {
    def linkedFile = Utils.getLinkedFile(node)
    if (!linkedFile?.exists()) return null

    def docDir = Utils.loadDocDir(node)
    def docName = node.text
    def pageFile = new File(docDir, docName + '.md')
    if (linkedFile == pageFile && pageFile.exists()) {
        return Utils.DOC_TARGET_PAGE
    }

    def directoryDir = new File(docDir, docName)
    if (linkedFile == directoryDir && directoryDir.exists() && directoryDir.isDirectory()) {
        return Utils.DOC_TARGET_DIRECTORY
    }

    return null
}

def static getPageFile(node) {
    // Look for a file linked from the node.
    def pageFile = Utils.getLinkedFile(node)
    if (!pageFile) return null

    // Ignore the file if it doesn't match the node text.
    def docDir = Utils.loadDocDir(node)
    def pageName = node.text
    if (pageFile != new File(docDir, pageName + '.md')) return null

    // Alert if pageFile doesn't exist.
    if (!pageFile.exists()) throw new RuntimeException('page file is missing.')

    return pageFile
}


def static updateNextSteps(node) {
    def pageFile = Utils.getPageFile(node)
    if (!pageFile) return 

    pageFile.withReader { reader ->
        while (true) {
            String ln = reader.readLine()
            if (ln == null || ln == '### Next Steps') break
        }

        for (c in node.getChildren()) c.delete()

        int cnt = 0
        while (true) {
            String ln = reader.readLine()
            if (ln == null || ln.startsWith('#')) break

            if ((ln.startsWith('* ') || ln.startsWith('- ')) && ln.length() > 3) {
                def newNode = node.createChild()
                newNode.text = ln.substring(2)
                cnt++
                if (cnt == 3) break
            }
        }
    }
}
