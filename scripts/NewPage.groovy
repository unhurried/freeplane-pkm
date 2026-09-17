// @ExecutionModes({ON_SELECTED_NODE})
def docDir = Utils.loadDocDir(node)
def pageName = node.text

if (!Utils.isValidName(pageName)) {
    ui.errorMessage('page name includes invalid characters')
    return
}

def templateFile = new File(c.getUserDirectory(), 'scripts/template.md')
if (!templateFile.exists()) {
    ui.errorMessage('template file is missing.')
    return
}

def pageFile = Utils.pageFile(docDir, pageName)
if (pageFile.exists()) {
    node.link.file = pageFile
    ui.errorMessage('page file already exists.')
    return
}

def pageAssetsDir = Utils.assetsDir(docDir, pageName)
if (pageAssetsDir.exists()) {
    ui.errorMessage('page assets directory already exists.')
    return
}

pageAssetsDir.mkdir()

def pageText = Utils.readPage(templateFile)
pageText = pageText.replace('${page_assets_name}', pageAssetsDir.name)
pageText = pageText.replace('${today}', new Date().format(Utils.DATE_FORMAT))
Utils.writePage(pageFile, pageText)

node.link.file = pageFile
Utils.openInDesktop(pageFile)
