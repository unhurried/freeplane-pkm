def static loadPageDirPath(c, ui) {
    def configNode
    for (child in c.getViewRoot().children) {
        if (child.text == 'config') {
            configNode = child
            break
        }
    }

    if (!configNode) {
        ui.errorMessage('config node is missing.')
        return
    }

    def pageDirPath
    for (child in configNode.children) {
        if (child.text == 'pageDirPath' && child.children[0]) {
            pageDirPath = child.children[0].plainText
        }
    }

    if (!pageDirPath) {
        ui.errorMessage('pageDirPath node is missing.')
        return
    }

    return pageDirPath
}

def static updateNextSteps(pageFile, node) {
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
