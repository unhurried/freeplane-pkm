def pageDirPath = Utils.loadPageDirPath(c, ui)

def pageName = node.text

def newPageName = ui.showInputDialog(node.delegate, "New Page Name", null)
if (newPageName == null || newPageName.isEmpty()) {
    return
}
if (newPageName =~ '[\\\\/:*?"><|]') {
    ui.errorMessage('new page name includes invalid characters')
    return
}

def pageDir = new File(pageDirPath)
if (!pageDir.exists()) {
    ui.errorMessage('page directory is missimg.')
    return
}

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

def newPageFile = new File(pageDir, newPageName + '.md')
if (newPageFile.exists()) {
    ui.errorMessage('new page file already exists.')
    return
}

def newPageAssetsDir = new File(pageDir, newPageName + '.assets')
if (newPageAssetsDir.exists()) {
    ui.errorMessage('new page assets directory already exists.')
    return
}

pageFile.renameTo(newPageFile)
pageAssetsDir.renameTo(newPageAssetsDir)

def pageText = newPageFile.text
pageText = pageText.replaceAll(pageName + '.md', newPageName + '.md')
pageText = pageText.replaceAll(pageName + '.assets', newPageName + '.assets')

newPageFile.createNewFile()
byte[] BOM = [ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF ];
newPageFile.setBytes(BOM)
newPageFile.append(pageText, "UTF-8")

node.text = newPageName
node.link.file = newPageFile
