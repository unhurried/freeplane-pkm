def static loadPageDir(node) {
    def configNode
    for (child in node.mindMap.root.children) {
        if (child.text == 'config') {
            configNode = child
            break
        }
    }
    if (!configNode) throw new RuntimeException('config node is missing.')

    def pageDirPath
    for (child in configNode.children) {
        if (child.text == 'pageDirPath' && child.children[0]) {
            pageDirPath = child.children[0].plainText
        }
    }
    if (!pageDirPath) throw new RuntimeException('pageDirPath node is missing.')

    def pageDir = new File(pageDirPath)
    if (!pageDir.exists()) throw new RuntimeException('page directory is missing.')

    return pageDir
}

def static getPageFile(node) {
    // Look for a file linked from the node.
    def pageFile
    if (node.link.node && node.link.node.link.file) {
        pageFile = node.link.node.link.file
    } else if (node.link.file) {
        pageFile = node.link.file
    } else {
        return null
    }

    // Ignore the file if it doesn't match the node text.
    def pageDir = Utils.loadPageDir(node)
    def pageName = node.text
    if (pageFile != new File(pageDir, pageName + '.md')) return null

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
