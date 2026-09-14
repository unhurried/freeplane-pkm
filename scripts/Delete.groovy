import javax.swing.JOptionPane

def deletePage(ui, node, docDir, pageName) {
    if (!confirmAction(ui, node, "Are you sure to delete \"${pageName}\"?")) return

    new File(docDir, pageName + '.md').delete()
    def pageAssetsDir = new File(docDir, pageName + '.assets')
    if (pageAssetsDir.exists()) pageAssetsDir.deleteDir()
    node.delete()
}

def deleteDirectory(ui, node, docDir, directoryName) {
    if (!confirmAction(ui, node, "Are you sure to delete directory \"${directoryName}\"?")) return

    if (!new File(docDir, directoryName).deleteDir()) {
        ui.errorMessage('failed to delete directory.')
        return
    }

    node.delete()
}

def confirmAction(ui, node, String message) {
    def option = ui.showConfirmDialog(node.delegate, message, 'Delete', JOptionPane.YES_NO_OPTION)
    return option == JOptionPane.YES_OPTION
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
