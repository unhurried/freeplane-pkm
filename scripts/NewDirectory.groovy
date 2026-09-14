def docDir = Utils.loadDocDir(node)
def directoryName = node.text

if (!Utils.isValidName(directoryName)) {
    ui.errorMessage('directory name includes invalid characters')
    return
}

def directoryDir = Utils.directoryDir(docDir, directoryName)
if (directoryDir.exists()) {
    ui.errorMessage('directory already exists.')
    return
}

directoryDir.mkdir()

node.link.file = directoryDir
Utils.openInDesktop(directoryDir)
