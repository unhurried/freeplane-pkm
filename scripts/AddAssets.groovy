def pageFile = Utils.getPageFile(node)
if (!pageFile) {
    ui.errorMessage('target node is not a page.')
    return
}

def docDir = Utils.loadDocDir(node)
def pageName = node.text
def pageAssetsDir = new File(docDir, pageName + '.assets')

if (pageAssetsDir.exists()) {
    ui.errorMessage('page assets directory already exists.')
    return
}

pageAssetsDir.mkdir()

def pageText = pageFile.getText('UTF-8')
if (pageText.startsWith('\uFEFF')) {
    pageText = pageText.substring(1)
}

def assetsLink = "[assets](${pageAssetsDir.getName()})\n\n"
pageText = assetsLink + pageText

byte[] BOM = [(byte) 0xEF, (byte) 0xBB, (byte) 0xBF]
pageFile.bytes = BOM
pageFile.append(pageText, 'UTF-8')
