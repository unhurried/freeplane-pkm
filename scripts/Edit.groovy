// If the node links to a PKM page or directory, rename it on disk (and update the
// mind map node/link accordingly) instead of editing its text directly, since a
// plain text edit would desynchronize the node from the file system.
// Otherwise, delegate to the built-in MindMap/EditAction (default shortcut F2).
// The built-in action cannot be shipped with an add-on shortcut, so this script
// delegates to it, keeping the behavior identical.

import org.freeplane.features.mode.Controller
import java.awt.event.ActionEvent

def editWithBuiltinAction(ui, node) {
    def key = 'EditAction'
    def action = Controller.currentModeController.getAction(key)
    if (action == null) {
        ui.errorMessage("Action not found: $key")
        return
    }
    action.actionPerformed(new ActionEvent(node.delegate, ActionEvent.ACTION_PERFORMED, key))
}

def renamePage(ui, node, docDir, pageName, newName) {
    def pageFile = new File(docDir, pageName + '.md')
    if (!pageFile.exists()) {
        ui.errorMessage('page file is missing.')
        return
    }

    def pageAssetsDir = new File(docDir, pageName + '.assets')

    def newPageFile = new File(docDir, newName + '.md')
    if (newPageFile.exists()) {
        ui.errorMessage('new page file already exists.')
        return
    }

    def newPageAssetsDir = new File(docDir, newName + '.assets')
    if (newPageAssetsDir.exists()) {
        ui.errorMessage('new page assets directory already exists.')
        return
    }

    if (!pageFile.renameTo(newPageFile)) {
        ui.errorMessage('failed to rename page file.')
        return
    }
    if (pageAssetsDir.exists() && !pageAssetsDir.renameTo(newPageAssetsDir)) {
        newPageFile.renameTo(pageFile)
        ui.errorMessage('failed to rename page assets directory.')
        return
    }

    def pageText = newPageFile.text
    pageText = pageText.replaceAll(pageName + '.md', newName + '.md')
    pageText = pageText.replaceAll(pageName + '.assets', newName + '.assets')

    byte[] BOM = [(byte) 0xEF, (byte) 0xBB, (byte) 0xBF]
    newPageFile.bytes = BOM
    newPageFile.append(pageText, 'UTF-8')

    node.text = newName
    node.link.file = newPageFile
}

def renameDirectory(ui, node, docDir, directoryName, newName) {
    def directoryDir = new File(docDir, directoryName)
    if (!directoryDir.exists() || !directoryDir.isDirectory()) {
        ui.errorMessage('directory is missing.')
        return
    }

    def newDirectoryDir = new File(docDir, newName)
    if (newDirectoryDir.exists()) {
        ui.errorMessage('new directory already exists.')
        return
    }

    if (!directoryDir.renameTo(newDirectoryDir)) {
        ui.errorMessage('failed to rename directory.')
        return
    }

    node.text = newName
    node.link.file = newDirectoryDir
}

def docNodeType = Utils.getDocNodeType(node)
if (docNodeType == null) {
    editWithBuiltinAction(ui, node)
    return
}

def INVALID_CHARS_PATTERN = '[\\\\/:*?"><|]'

def newName = ui.showInputDialog(node.delegate, 'New Name', node.text)
if (newName == null || newName.isEmpty()) {
    return
}
if (newName =~ INVALID_CHARS_PATTERN) {
    ui.errorMessage('new name includes invalid characters')
    return
}

def docName = node.text
def docDir = Utils.loadDocDir(node)

if (docNodeType == Utils.DOC_TARGET_PAGE) {
    renamePage(ui, node, docDir, docName, newName)
    return
}

if (docNodeType == Utils.DOC_TARGET_DIRECTORY) {
    renameDirectory(ui, node, docDir, docName, newName)
    return
}
