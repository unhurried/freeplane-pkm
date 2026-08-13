def docDir = Utils.loadDocDir(node)
def pageName = node.text

def INVALID_CHARS_PATTERN = '[\\\\/:*?"><|]'
if (pageName =~ INVALID_CHARS_PATTERN) {
    ui.errorMessage('page name includes invalid characters')
    return
}

def templateFile = new File(c.getUserDirectory(), 'scripts/template.md')
if (!templateFile.exists()) {
    ui.errorMessage('template file is missing.')
    return
}

def pageFile = new File(docDir, pageName + '.md')
if (pageFile.exists()) {
    node.link.file = pageFile
    ui.errorMessage('page file already exists.')
    return
}

def pageAssetsDir = new File(docDir, pageName + '.assets')
if (pageAssetsDir.exists()) {
    ui.errorMessage('page assets directory already exists.')
    return
}

pageAssetsDir.mkdir()

def pageText = templateFile.text
pageText = pageText.replace('${page_assets_name}', pageAssetsDir.getName())
pageText = pageText.replace('${today}', new Date().format('yy/MM/dd'))

byte[] BOM = [(byte) 0xEF, (byte) 0xBB, (byte) 0xBF]
pageFile.bytes = BOM
pageFile.append(pageText, 'UTF-8')

node.link.file = pageFile
Utils.openInDesktop(pageFile)
