def pageDirPath = Utils.loadPageDirPath(c, ui)

def pageName = node.text
if (pageName =~ '[\\\\/:*?"><|]') {
    ui.errorMessage('page name includes invalid characters')
    return
}

def templateFile = new File(c.getUserDirectory(), 'scripts/template.md')
if (!templateFile.exists()) {
    ui.errorMessage('template file is missimg.')
    return
}

def pageDir = new File(pageDirPath)
if (!pageDir.exists()) {
    ui.errorMessage('page directory is missimg.')
    return
}

def pageFile = new File(pageDir, pageName + '.md')
if (pageFile.exists()) {
    node.link.file = pageFile
    ui.errorMessage('page file already exists.')
    return
}

def pageAssetsDir = new File(pageDir, pageName + '.assets')
if (pageAssetsDir.exists()) {
    ui.errorMessage('page assets directory already exists.')
    return
}

pageAssetsDir.mkdir()

def pageText = templateFile.text
def pageAssetsName = pageAssetsDir.getName()
pageText = pageText.replace('${page_assets_name}', pageAssetsName)
def today = new Date().format('yyyy/MM/dd')
pageText = pageText.replace('${today}', today)

pageFile.createNewFile()
byte[] BOM = [ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF ];
pageFile.append(BOM)
pageFile.append(pageText, "UTF-8")

node.link.file = pageFile
java.awt.Desktop.getDesktop().open(pageFile)
