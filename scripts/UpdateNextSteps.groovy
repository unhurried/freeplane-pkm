def pageName = node.text
def pageDirPath = Utils.loadPageDirPath(c, ui)
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

pageFile.withReader { reader ->
    while ((line = reader.readLine()) != null) {
        if (line == '### Next Steps') break
    }

    for (c in node.getChildren()) c.delete()

    while ((line = reader.readLine()) != null) {
        if (line.startsWith('#')) break
        if (line.startsWith('* ') && line.length() > 3) {
            def newNode = node.createChild()
            newNode.text = line.substring(2)
            break
        }
    }
}
