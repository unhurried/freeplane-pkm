def static loadPageDirPath(c, ui) {

    def configNode
    for (child in c.getViewRoot().children) {
        if (child.text == 'config') {
            configNode = child
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
