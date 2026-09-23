import groovy.transform.Field
import javax.swing.SwingUtilities

@Field static final DOC_TARGET_PAGE = 'page'
@Field static final DOC_TARGET_DIRECTORY = 'directory'

/** Date format used for due dates and journal entries throughout the map and pages. */
@Field static final String DATE_FORMAT = 'yy/MM/dd'
/** The node under the root that holds the task list. */
@Field static final String TODO_NODE_NAME = 'ToDo'

@Field private static final String CONFIG_NODE_NAME = 'config'
@Field private static final String DOC_DIR_PATH_KEY = 'docDirPath'
@Field private static final String PAGE_SUFFIX = '.md'
@Field private static final String ASSETS_SUFFIX = '.assets'
@Field private static final String NEXT_STEPS_HEADING = '### Next Steps'
@Field private static final int MAX_NEXT_STEPS = 3
// "* " / "- " plus at least 2 more characters, so "* x" isn't a list item.
@Field private static final int MIN_LIST_ITEM_LENGTH = 3
// File system reserved characters, rejected in page and directory names.
@Field private static final String INVALID_NAME_CHARS = '[\\\\/:*?"><|]'
@Field private static final String BOM_CHAR = '\uFEFF'

// --- Document directory and node <-> file mapping ---

/** The document directory from the map's config node: root > config > docDirPath > [path]. */
def static loadDocDir(node) {
    def configNode = findChildByText(node.mindMap.root, CONFIG_NODE_NAME)
    if (!configNode) throw new RuntimeException('config node is missing.')

    def docDirPath = findChildByText(configNode, DOC_DIR_PATH_KEY)?.children?.getAt(0)?.plainText
    if (!docDirPath) throw new RuntimeException('docDirPath node is missing.')

    def docDir = new File(docDirPath)
    if (!docDir.exists()) throw new RuntimeException('document directory is missing.')

    return docDir
}

def static findChildByText(parentNode, String text) {
    return parentNode.children.find { it.text == text }
}

/** The text of a root > ToDo item copied from a page: "item (page)". */
def static String toDoItemText(String item, String page) {
    return item + toDoItemSuffix(page)
}

/**
 * Points the root > ToDo items copied from the page oldName (the way AddToToDo
 * creates them: "item (oldName)" linking <docDir>/<oldName>.md) at its new
 * name. Without this, the next refresh sees them linking a page that no longer
 * exists and deletes them (see updateAllNextSteps).
 */
def static void renameToDoItems(root, File docDir, String oldName, String newName) {
    def toDoNode = findChildByText(root, TODO_NODE_NAME)
    if (!toDoNode) return

    def oldFile = pageFile(docDir, oldName)
    def oldSuffix = toDoItemSuffix(oldName)
    for (item in toDoNode.children) {
        if (item.link.file != oldFile) continue
        if (item.text.endsWith(oldSuffix)) {
            item.text = toDoItemText(item.text.substring(0, item.text.length() - oldSuffix.length()), newName)
        }
        item.link.file = pageFile(docDir, newName)
    }
}

def static File pageFile(File docDir, String name) {
    return new File(docDir, name + PAGE_SUFFIX)
}

def static File assetsDir(File docDir, String name) {
    return new File(docDir, name + ASSETS_SUFFIX)
}

def static File directoryDir(File docDir, String name) {
    return new File(docDir, name)
}

/** The page name of a <name>.md file, or null when the name has no ".md" suffix. */
def static String pageNameOf(File file) {
    return file.name.endsWith(PAGE_SUFFIX) ? file.name - PAGE_SUFFIX : null
}

/**
 * Whether a path relative to the document directory is a page: a <name>.md file
 * outside any "<page>.assets/" directory. Everything else is an attachment.
 */
def static boolean isPagePath(String relPath) {
    def segments = relPath.tokenize('/')
    return segments.last().endsWith(PAGE_SUFFIX) && !segments.init().any { it.endsWith(ASSETS_SUFFIX) }
}

/** The page file an assets directory belongs to, or null when the name has no ".assets" suffix. */
def static File pageFileOfAssetsDir(File assetsDir) {
    if (!assetsDir.name.endsWith(ASSETS_SUFFIX)) return null
    return pageFile(assetsDir.parentFile, assetsDir.name - ASSETS_SUFFIX)
}

def static boolean isValidName(String name) {
    return !(name =~ INVALID_NAME_CHARS)
}

/** Reads a page as UTF-8, without the byte-order mark writePage() puts in front. */
def static String readPage(File file) {
    def text = file.getText('UTF-8')
    return text.startsWith(BOM_CHAR) ? text.substring(1) : text
}

/** Writes a page as UTF-8 with a byte-order mark (what Markdown editors on Windows expect). */
def static void writePage(File file, String text) {
    file.setText(BOM_CHAR + text, 'UTF-8')
}

/** The file linked from the node, directly or via a linked node. */
def static getLinkedFile(node) {
    return node.link.node?.link?.file ?: node.link.file
}

/**
 * DOC_TARGET_PAGE if the node links the existing file <docDir>/<text>.md,
 * DOC_TARGET_DIRECTORY if it links the existing directory <docDir>/<text>/, else null.
 * Pass docDir when it is already loaded (callers that classify many nodes).
 */
def static getDocNodeType(node, File docDir = null) {
    def linkedFile = getLinkedFile(node)
    if (!linkedFile) return null

    def dir = docDir ?: loadDocDir(node)
    if (linkedFile == pageFile(dir, node.text) && linkedFile.isFile()) return DOC_TARGET_PAGE
    if (linkedFile == directoryDir(dir, node.text) && linkedFile.isDirectory()) return DOC_TARGET_DIRECTORY
    return null
}

/**
 * Maps every file linked from the subtree at node to the node linking it, keyed
 * by normalizedFile(). A direct link (node.link.file) wins over an indirect one
 * (through node.link.node), so a "see also" link never shadows the node that
 * represents the document.
 */
def static Map<File, Object> collectNodesByLinkedFile(node) {
    def directNodesByFile = [:]
    def indirectNodesByFile = [:]
    eachNode(node) { n ->
        if (n.link.file) {
            directNodesByFile[normalizedFile(n.link.file)] = n
            return
        }
        def linkedFile = getLinkedFile(n)
        if (linkedFile) indirectNodesByFile[normalizedFile(linkedFile)] = n
    }
    return indirectNodesByFile + directNodesByFile
}

/**
 * Resolves a file (e.g. a search hit) to its node: the node linking the file
 * itself, else the nearest ancestor directory's node, else - for a file under
 * "<page>.assets/" - the page's node. Null if none of those is linked.
 */
def static findNodeForFile(Map<File, Object> nodesByFile, File file, File docDir) {
    def normalizedDocDir = normalizedFile(docDir)
    def current = normalizedFile(file)
    def hit = nodesByFile[current]
    if (hit) return hit

    def dir = current.parentFile
    while (dir != null) {
        hit = nodesByFile[dir] ?: nodesByFile[pageFileOfAssetsDir(dir)]
        if (hit) return hit

        if (dir == normalizedDocDir) break
        dir = dir.parentFile
    }
    return null
}

/**
 * Absolute path with "." and ".." folded away - pure path arithmetic, unlike
 * File.getCanonicalFile(), which hits the file system. Symlinks are not resolved.
 */
def static File normalizedFile(File file) {
    return file.toPath().toAbsolutePath().normalize().toFile()
}

/** "root > ... > text" for a node. */
def static nodePathText(node) {
    def parts = []
    def current = node
    while (current != null) {
        parts.add(0, current.text)
        current = current.getParent()
    }
    return parts.join(' > ')
}

/** Deletes every child of the node (children is a copy, so deleting while iterating is safe). */
def static void deleteChildren(node) {
    node.children*.delete()
}

/**
 * Opens a file or directory in the OS's default application. Lives here, not
 * inline in the scripts, so specs can replace it: Desktop is unusable headless.
 */
def static openInDesktop(File file) {
    java.awt.Desktop.getDesktop().open(file)
}

// --- Derived state: Next Steps children and the search index ---

/**
 * Refreshes both pieces of derived state - every page's Next Steps children and
 * the full-text index - in the background. Fails fast (synchronously) when the
 * document directory is not configured.
 */
def static updateNextStepsAndIndex(node) {
    def docDir = loadDocDir(node)
    updateAllNextSteps(node, docDir)
    Thread.start { SearchIndex.updateIndex(docDir) }
}

/**
 * Syncs the "### Next Steps" list items of every page in the map into the page
 * node's children, and drops the root > ToDo items (see AddToToDo.groovy) whose
 * text no longer appears among their page's Next Steps. Files are read on a
 * background thread; the node mutations are then applied in one batch on the
 * Swing EDT, and only for pages whose items actually differ from the current
 * children, so an unchanged map stays untouched (and unmodified).
 */
def static updateAllNextSteps(node, File docDir = null) {
    def map = node.mindMap
    def root = map.root
    def dir = docDir ?: loadDocDir(root)
    def targets = collectNodes(root)

    Thread.start {
        // Every page is read once, whether page nodes or ToDo items (or both) refer to it.
        def itemsByFile = [:].withDefault { readNextStepItems(it) }
        def nextSteps = [:]
        for (target in targets) {
            if (getDocNodeType(target, dir) != DOC_TARGET_PAGE) continue
            def items = itemsByFile[pageFile(dir, target.text)]
            if (items != null) nextSteps[target] = items.take(MAX_NEXT_STEPS)
        }
        def staleToDoItems = collectStaleToDoItems(root, dir, itemsByFile)
        SwingUtilities.invokeLater {
            def changed = nextSteps.findAll { target, lines -> target.children*.text != lines }
            changed.each { target, lines -> applyNextSteps(target, lines) }
            staleToDoItems*.delete()
            // Only creating nodes leaves the filter stale (see reapplyFilter).
            if (changed.any { target, lines -> lines }) reapplyFilter(map)
        }
    }
}

/**
 * Re-applies the map's current filter, the way the built-in "Reapply filter"
 * action does. Freeplane computes a filter result for a newly created node
 * only in map views other than the active one; an unchecked node counts as
 * visible, so a Next Steps child created under a page the filter hides (an
 * archived page under the archive filter, any page under the ToDo filter)
 * would be drawn hanging off its nearest visible ancestor, usually the root.
 * Lives here, not inline, so specs can replace it: FilterController and
 * Controller exist only inside Freeplane, hence the reflective lookups.
 */
def static reapplyFilter(map) {
    def mapModel = map.delegate
    def selection = freeplaneClass('org.freeplane.features.mode.Controller').currentController.selection
    // Only the active map view needs it, and only while a filter is on.
    if (selection?.map != mapModel || selection.filter?.condition == null) return
    freeplaneClass('org.freeplane.features.filter.FilterController').currentFilterController
            .applyFilter(mapModel, true, selection.filter)
}

// --- Private helper methods ---

/** A Freeplane class, resolved at runtime because Freeplane isn't a build dependency. */
private static Class freeplaneClass(String name) {
    return Class.forName(name, true, Utils.classLoader)
}

private static void eachNode(node, Closure action) {
    action(node)
    for (child in node.children) {
        eachNode(child, action)
    }
}

private static List collectNodes(node) {
    def nodes = []
    eachNode(node) { nodes << it }
    return nodes
}

/**
 * Every list item under the page's Next Steps heading, without the list marker.
 * Null when the file can't be read, so one broken link doesn't interrupt the
 * scan of the rest of the map.
 */
private static List<String> readNextStepItems(File pageFile) {
    try {
        def lines = readPage(pageFile).readLines()
        def start = lines.indexOf(NEXT_STEPS_HEADING)
        if (start < 0) return []
        return lines.drop(start + 1).takeWhile { !it.startsWith('#') }
                .findAll { isListItem(it) }*.substring(2)
    } catch (Exception ignored) {
        return null
    }
}

private static String toDoItemSuffix(String page) {
    return " (${page})"
}

private static boolean isListItem(String line) {
    return (line.startsWith('* ') || line.startsWith('- ')) && line.length() > MIN_LIST_ITEM_LENGTH
}

/**
 * The root > ToDo children linking <docDir>/<page>.md (the way AddToToDo creates
 * them) whose text is no longer among that page's Next Steps items in
 * itemsByFile (an unreadable page counts). Reads files, so runs off the EDT.
 */
private static List collectStaleToDoItems(root, File docDir, Map<File, List<String>> itemsByFile) {
    def toDoNode = findChildByText(root, TODO_NODE_NAME)
    if (!toDoNode) return []

    return toDoNode.children.findAll { item ->
        def linkedFile = item.link.file
        def pageName = linkedFile ? pageNameOf(linkedFile) : null
        if (!pageName || linkedFile != pageFile(docDir, pageName)) return false

        def items = itemsByFile[linkedFile]
        return items == null || !items.any { toDoItemText(it, pageName) == item.text }
    }
}

private static void applyNextSteps(node, List<String> lines) {
    deleteChildren(node)
    for (line in lines) {
        node.createChild().text = line
    }
}
