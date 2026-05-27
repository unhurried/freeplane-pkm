import javax.swing.JOptionPane

def deletePage(ui, node, docDir, pageName) {
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

    def option = ui.showConfirmDialog(node.delegate, 'Are you sure to delete "' + pageName + '"?', 'Delete', JOptionPane.YES_NO_OPTION)
    if (option != JOptionPane.YES_OPTION) {
        return
    }

    pageFile.delete()
    pageAssetsDir.deleteDir()
    node.delete()
}

def deleteDirectory(ui, node, docDir, directoryName) {
    def directoryDir = new File(docDir, directoryName)
    if (!directoryDir.exists() || !directoryDir.isDirectory()) {
        ui.errorMessage('directory is missing.')
        return
    }

    def option = ui.showConfirmDialog(node.delegate, 'Are you sure to delete directory "' + directoryName + '"?', 'Delete', JOptionPane.YES_NO_OPTION)
    if (option != JOptionPane.YES_OPTION) {
        return
    }

    if (!directoryDir.deleteDir()) {
        ui.errorMessage('failed to delete directory.')
        return
    }

    node.delete()
}

def docDir = Utils.loadDocDir(node)
def docName = node.text
def docNodeType = Utils.getDocNodeType(node)

if (docNodeType == Utils.DOC_TARGET_PAGE) {
    deletePage(ui, node, docDir, docName)
    return
}

if (docNodeType == Utils.DOC_TARGET_DIRECTORY) {
    deleteDirectory(ui, node, docDir, docName)
    return
}

ui.errorMessage('target node is neither page nor directory.')
