// Creates the page's assets directory and links it from the top of the page.
def docDir = Utils.loadDocDir(node)
if (Utils.getDocNodeType(node, docDir) != Utils.DOC_TARGET_PAGE) {
    ui.errorMessage('target node is not a page.')
    return
}

def pageFile = Utils.pageFile(docDir, node.text)
def pageAssetsDir = Utils.assetsDir(docDir, node.text)

if (pageAssetsDir.exists()) {
    ui.errorMessage('page assets directory already exists.')
    return
}

pageAssetsDir.mkdir()
Utils.writePage(pageFile, "[assets](${pageAssetsDir.name})\n\n" + Utils.readPage(pageFile))
