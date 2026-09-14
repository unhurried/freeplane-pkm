import javax.swing.JOptionPane

def docDir = Utils.loadDocDir(node)
def docNodeType = Utils.getDocNodeType(node, docDir)
if (docNodeType == null) {
    ui.errorMessage('target node is neither page nor directory.')
    return
}

def docName = node.text
def what = docNodeType == Utils.DOC_TARGET_PAGE ? "\"${docName}\"" : "directory \"${docName}\""
def option = ui.showConfirmDialog(node.delegate, "Are you sure to delete ${what}?", 'Delete', JOptionPane.YES_NO_OPTION)
if (option != JOptionPane.YES_OPTION) return

if (docNodeType == Utils.DOC_TARGET_PAGE) {
    Utils.pageFile(docDir, docName).delete()
    def pageAssetsDir = Utils.assetsDir(docDir, docName)
    if (pageAssetsDir.exists()) pageAssetsDir.deleteDir()
} else if (!Utils.directoryDir(docDir, docName).deleteDir()) {
    ui.errorMessage('failed to delete directory.')
    return
}

node.delete()
