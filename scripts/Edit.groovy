// A page or directory node is renamed on disk (and its node/link updated), since
// editing the text alone would desynchronize the node from the file system. Any
// other node just gets its text edited through the same input dialog.
//
// The rename methods reach node and ui through the script bindings; only docDir,
// a script-local variable, has to be passed in.

def renamePage(File docDir, String newName) {
    def oldName = node.text
    def pageFile = Utils.pageFile(docDir, oldName)
    def pageAssetsDir = Utils.assetsDir(docDir, oldName)
    def newPageFile = Utils.pageFile(docDir, newName)
    def newPageAssetsDir = Utils.assetsDir(docDir, newName)

    if (newPageFile.exists()) {
        ui.errorMessage('new page file already exists.')
        return
    }
    if (newPageAssetsDir.exists()) {
        ui.errorMessage('new page assets directory already exists.')
        return
    }

    if (!pageFile.renameTo(newPageFile)) {
        ui.errorMessage('failed to rename page file.')
        return
    }
    if (pageAssetsDir.exists() && !pageAssetsDir.renameTo(newPageAssetsDir)) {
        newPageFile.renameTo(pageFile)
        ui.errorMessage('failed to rename page assets directory.')
        return
    }

    // Literal replace, not regex: page names may contain metacharacters ("C++ Memo").
    def pageText = Utils.readPage(newPageFile)
    pageText = pageText.replace(pageFile.name, newPageFile.name)
    pageText = pageText.replace(pageAssetsDir.name, newPageAssetsDir.name)
    Utils.writePage(newPageFile, pageText)

    node.text = newName
    node.link.file = newPageFile
    // The ToDo items copied from this page name it too; the next refresh would
    // otherwise drop them as items of a page that no longer exists.
    Utils.renameToDoItems(node.mindMap.root, docDir, oldName, newName)
}

def renameDirectory(File docDir, String newName) {
    def newDirectoryDir = Utils.directoryDir(docDir, newName)
    if (newDirectoryDir.exists()) {
        ui.errorMessage('new directory already exists.')
        return
    }
    if (!Utils.directoryDir(docDir, node.text).renameTo(newDirectoryDir)) {
        ui.errorMessage('failed to rename directory.')
        return
    }

    node.text = newName
    node.link.file = newDirectoryDir
}

def docNodeType = Utils.getDocNodeType(node)
if (docNodeType == null) {
    def newText = ui.showInputDialog(node.delegate, 'New Text', node.text)
    if (newText) node.text = newText
    return
}

def newName = ui.showInputDialog(node.delegate, 'New Name', node.text)
if (!newName) return
if (!Utils.isValidName(newName)) {
    ui.errorMessage('new name includes invalid characters')
    return
}

def docDir = Utils.loadDocDir(node)
if (docNodeType == Utils.DOC_TARGET_PAGE) {
    renamePage(docDir, newName)
} else {
    renameDirectory(docDir, newName)
}
