// @ExecutionModes({ON_SINGLE_NODE})
// Opens every selected page node's .md file or directory node's directory in the
// OS default application; any other selected node is skipped.
def docDir = Utils.loadDocDir(node)
c.selecteds.each { n ->
    if (Utils.getDocNodeType(n, docDir) != null) {
        Utils.openInDesktop(Utils.getLinkedFile(n))
    }
}
