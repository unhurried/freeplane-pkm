// @ExecutionModes({ON_SINGLE_NODE})
// Deletes the selected page and directory nodes together with their files after
// one confirmation listing them all; nothing is deleted while any selected node
// is neither a page nor a directory.
import javax.swing.JOptionPane

def docDir = Utils.loadDocDir(node)
// A selected node under another selected node is deleted with that node's directory.
def targets = c.getSortedSelection(true).collect { n -> [node: n, type: Utils.getDocNodeType(n, docDir)] }

def others = targets.findAll { it.type == null }
if (others) {
    ui.errorMessage("not a page or directory: ${others*.node*.text.join(', ')}")
    return
}

def describe = { t -> t.type == Utils.DOC_TARGET_PAGE ? "\"${t.node.text}\"" : "directory \"${t.node.text}\"" }
def question = targets.size() == 1 ? "Are you sure to delete ${describe(targets[0])}?"
                                   : "Are you sure to delete the following?\n${targets.collect(describe).join('\n')}"
def option = ui.showConfirmDialog(node.delegate, question, 'Delete', JOptionPane.YES_NO_OPTION)
if (option != JOptionPane.YES_OPTION) return

def failed = []
targets.each { t ->
    def docName = t.node.text
    if (t.type == Utils.DOC_TARGET_PAGE) {
        Utils.pageFile(docDir, docName).delete()
        def pageAssetsDir = Utils.assetsDir(docDir, docName)
        if (pageAssetsDir.exists()) pageAssetsDir.deleteDir()
    } else if (!Utils.directoryDir(docDir, docName).deleteDir()) {
        failed << docName
        return
    }
    t.node.delete()
}
if (failed) ui.errorMessage("failed to delete directory: ${failed.join(', ')}")
