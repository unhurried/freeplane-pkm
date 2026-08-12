/**
 * Stand-in for the node proxy Freeplane binds as "node", covering the slice of its
 * API the add-on's scripts actually use.
 *
 * A real class rather than an Expando (which UtilsSpec uses for its flatter, parentless
 * fixtures): these nodes form a cyclic parent/child graph, and Expando.toString()
 * renders its properties recursively, so any failure message that mentions a node
 * overflows the stack. Gradle's JUnit listener then dies while reporting and records
 * the test as skipped instead of failed - i.e. a green build hiding a broken test.
 */
class FakeNode {

    String text = ''
    private String plainTextOverride
    FakeNode parent
    /** The live child list; getChildren()/children hand out a copy, as Freeplane does. */
    final List<FakeNode> childNodes = []
    final FakeLink link = new FakeLink()
    final FakeIcons icons = new FakeIcons()
    def mindMap
    boolean left = true
    boolean deleted = false
    /** Dialog owner the scripts pass to ui.showInputDialog()/showConfirmDialog(). */
    def delegate

    String getPlainText() { plainTextOverride ?: text }

    void setPlainText(String value) { plainTextOverride = value }

    /** Freeplane exposes the map as both node.mindMap and node.map. */
    def getMap() { mindMap }

    /** A copy, like Freeplane's node model, so a script can delete children while iterating. */
    List<FakeNode> getChildren() { new ArrayList<FakeNode>(childNodes) }

    FakeNode createChild() { insertChild(new FakeNode()) }

    FakeNode createChild(int index) { insertChild(new FakeNode(), index) }

    FakeNode insertChild(FakeNode child, int index = -1) {
        child.parent = this
        child.mindMap = mindMap
        if (index < 0) {
            childNodes << child
        } else {
            childNodes.add(index, child)
        }
        return child
    }

    void removeChild(FakeNode child) {
        childNodes.remove(child)
        child.parent = null
    }

    void delete() {
        deleted = true
        parent?.childNodes?.remove(this)
        parent = null
    }

    @Override
    String toString() { "FakeNode(${text})" }

    static class FakeLink {
        File file
        FakeNode node
    }

    static class FakeIcons {
        List<String> icons = []

        void addIcon(String icon) { icons << icon }

        void removeIcon(String icon) { icons.remove(icon) }
    }
}
