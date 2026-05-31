def renamePage(ui, node, docDir, pageName, newName) {
    def pageFile = new File(docDir, pageName + '.md')
    if (!pageFile.exists()) {
        ui.errorMessage('page file is missing.')
        return
    }

    def pageAssetsDir = new File(docDir, pageName + '.assets')
    if (!pageAssetsDir.exists()) {
        ui.errorMessage('page assets directory is missing.')
        return
    }

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
    if (!pageAssetsDir.renameTo(newPageAssetsDir)) {
        newPageFile.renameTo(pageFile)
        ui.errorMessage('failed to rename page assets directory.')
        return
    }

    def pageText = newPageFile.text
    pageText = pageText.replaceAll(pageName + '.md', newName + '.md')
    pageText = pageText.replaceAll(pageName + '.assets', newName + '.assets')

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

def INVALID_CHARS_PATTERN = '[\\/:*?"><|]'

def newName = ui.showInputDialog(node.delegate, 'New Name', null)
if (newName == null || newName.isEmpty()) {
    return
}
if (newName =~ INVALID_CHARS_PATTERN) {
    ui.errorMessage('new name includes invalid characters')
    return
}

def docName = node.text
def docDir = Utils.loadDocDir(node)
def docNodeType = Utils.getDocNodeType(node)

if (docNodeType == Utils.DOC_TARGET_PAGE) {
    renamePage(ui, node, docDir, docName, newName)
    return
}

if (docNodeType == Utils.DOC_TARGET_DIRECTORY) {
    renameDirectory(ui, node, docDir, docName, newName)
    return
}

ui.errorMessage('target node is neither page nor directory.')
