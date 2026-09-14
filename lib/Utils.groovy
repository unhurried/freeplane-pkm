import groovy.transform.Field
import javax.swing.SwingUtilities

@Field static final DOC_TARGET_PAGE = 'page'
@Field static final DOC_TARGET_DIRECTORY = 'directory'

/** Date format used for due dates and journal entries throughout the map and pages. */
@Field static final String DATE_FORMAT = 'yy/MM/dd'

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
 * node's children. Files are read on a background thread; the node mutations
 * are then applied in one batch on the Swing EDT, and only for pages whose
 * items actually differ from the current children, so an unchanged map stays
 * untouched (and unmodified).
 */
def static updateAllNextSteps(node, File docDir = null) {
    def root = node.mindMap.root
    def dir = docDir ?: loadDocDir(root)
    def targets = collectNodes(root)

    Thread.start {
        def nextSteps = [:]
        for (target in targets) {
            def lines = readNextSteps(target, dir)
            if (lines != null) nextSteps[target] = lines
        }
        SwingUtilities.invokeLater {
            nextSteps.each { target, lines ->
                if (target.children*.text != lines) applyNextSteps(target, lines)
            }
        }
    }
}

// --- Private helper methods ---

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
 * The Next Steps items of a page node, or null for anything else: a node that
 * isn't a page, or one whose file can't be read (so one broken link doesn't
 * interrupt the scan of the rest of the map).
 */
private static List<String> readNextSteps(node, File docDir) {
    try {
        if (getDocNodeType(node, docDir) != DOC_TARGET_PAGE) return null
        def lines = pageFile(docDir, node.text).readLines('UTF-8')
        def start = lines.indexOf(NEXT_STEPS_HEADING)
        if (start < 0) return []
        return lines.drop(start + 1).takeWhile { !it.startsWith('#') }
                .findAll { isListItem(it) }.take(MAX_NEXT_STEPS)*.substring(2)
    } catch (Exception ignored) {
        return null
    }
}

private static boolean isListItem(String line) {
    return (line.startsWith('* ') || line.startsWith('- ')) && line.length() > MIN_LIST_ITEM_LENGTH
}

private static void applyNextSteps(node, List<String> lines) {
    deleteChildren(node)
    for (line in lines) {
        node.createChild().text = line
    }
}
