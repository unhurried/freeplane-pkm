// Opens a page node's .md file or a directory node's directory in the OS default
// application; does nothing for any other node.
if (Utils.getDocNodeType(node) != null) {
    Utils.openInDesktop(Utils.getLinkedFile(node))
}
