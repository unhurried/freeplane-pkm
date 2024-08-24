def pageDirPath = Utils.loadPageDirPath(c, ui)

def templateFile = new File(c.getUserDirectory(), 'scripts/find.search-ms.template')
if (!templateFile.exists()) {
    ui.errorMessage('template file is missimg.')
    return
}

keyword = ui.showInputDialog(node.delegate, "Search Keyword", null)
if (keyword == null || keyword.isEmpty()) {
    return
}

def searchText = templateFile.text
searchText = searchText.replace('${page_dir}', pageDirPath)
searchText = searchText.replace('${keyword}', keyword)


tempFile = File.createTempFile("find.", ".search-ms")
tempFile.append(searchText, "UTF-8")

java.awt.Desktop.getDesktop().open(tempFile)
