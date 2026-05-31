def docDir = Utils.loadDocDir(node)
def directoryName = node.text

def INVALID_CHARS_PATTERN = '[\\\\/:*?"><|]'
if (directoryName =~ INVALID_CHARS_PATTERN) {
    ui.errorMessage('directory name includes invalid characters')
    return
}

def directoryDir = new File(docDir, directoryName)
if (directoryDir.exists()) {
    ui.errorMessage('directory already exists.')
    return
}

directoryDir.mkdir()

node.link.file = directoryDir
java.awt.Desktop.getDesktop().open(directoryDir)
