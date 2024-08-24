import javax.swing.JOptionPane

def pageDirPath = Utils.loadPageDirPath(c, ui)

def pageDir = new File(pageDirPath)
if (!pageDir.exists()) {
    ui.errorMessage('page directory is missimg.')
    return
}

def pageName = node.text

def pageFile = new File(pageDir, pageName + '.md')
if (!pageFile.exists()) {
    ui.errorMessage('page file is missing.')
    return
}

def pageAssetsDir = new File(pageDir, pageName + '.assets')
if (!pageAssetsDir.exists()) {
    ui.errorMessage('page assets directory is missing.')
    return
}

def option = ui.showConfirmDialog(node.delegate, 'Are you sure to delete "' + pageName + '"?', "Delete", JOptionPane.YES_NO_OPTION)
if (option != JOptionPane.YES_OPTION) {
    return
}

pageFile.delete()
pageAssetsDir.deleteDir()

node.delete()
