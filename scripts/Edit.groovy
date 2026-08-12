// If the node links to a PKM page or directory, rename it on disk (and update the
// mind map node/link accordingly) instead of editing its text directly, since a
// plain text edit would desynchronize the node from the file system.
// Otherwise, edit the node text through an input dialog, the same way a page or
// directory name is edited, instead of the built-in in-place editor.

def editText(ui, node) {
    def newText = ui.showInputDialog(node.delegate, 'New Text', node.text)
    if (newText == null || newText.isEmpty()) {
        return
    }
    node.text = newText
}

def renamePage(ui, node, docDir, pageName, newName) {
    def pageFile = new File(docDir, pageName + '.md')
    if (!pageFile.exists()) {
        ui.errorMessage('page file is missing.')
        return
    }

    def pageAssetsDir = new File(docDir, pageName + '.assets')

    def newPageFile = new File(docDir, newName + '.md')
    if (newPageFile.exists()) {
        ui.errorMessage('new page file already exists.')
        return
    }

    def newPageAssetsDir = new File(docDir, newName + '.assets')
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

    // Literal (String.replace), not regex (String.replaceAll): a page name is free
    // text, and INVALID_CHARS_PATTERN below only rejects file system reserved
    // characters, so names may legitimately contain regex metacharacters. Treating
    // them as a pattern either throws (e.g. "Memo [1]") or, worse, silently fails to
    // match (e.g. "C++ Memo", where "++" is a valid possessive quantifier), leaving
    // the renamed page pointing at its old, no longer existing, assets directory.
    def pageText = newPageFile.text
    pageText = pageText.replace(pageName + '.md', newName + '.md')
    pageText = pageText.replace(pageName + '.assets', newName + '.assets')

    byte[] BOM = [(byte) 0xEF, (byte) 0xBB, (byte) 0xBF]
    newPageFile.bytes = BOM
    newPageFile.append(pageText, 'UTF-8')

    node.text = newName
    node.link.file = newPageFile
}

def renameDirectory(ui, node, docDir, directoryName, newName) {
    def directoryDir = new File(docDir, directoryName)
    if (!directoryDir.exists() || !directoryDir.isDirectory()) {
        ui.errorMessage('directory is missing.')
        return
    }

    def newDirectoryDir = new File(docDir, newName)
    if (newDirectoryDir.exists()) {
        ui.errorMessage('new directory already exists.')
        return
    }

    if (!directoryDir.renameTo(newDirectoryDir)) {
        ui.errorMessage('failed to rename directory.')
        return
    }

    node.text = newName
    node.link.file = newDirectoryDir
}

def docNodeType = Utils.getDocNodeType(node)
if (docNodeType == null) {
    editText(ui, node)
    return
}

def INVALID_CHARS_PATTERN = '[\\\\/:*?"><|]'

def newName = ui.showInputDialog(node.delegate, 'New Name', node.text)
if (newName == null || newName.isEmpty()) {
    return
}
if (newName =~ INVALID_CHARS_PATTERN) {
    ui.errorMessage('new name includes invalid characters')
    return
}

def docName = node.text
def docDir = Utils.loadDocDir(node)

if (docNodeType == Utils.DOC_TARGET_PAGE) {
    renamePage(ui, node, docDir, docName, newName)
    return
}

if (docNodeType == Utils.DOC_TARGET_DIRECTORY) {
    renameDirectory(ui, node, docDir, docName, newName)
    return
}
