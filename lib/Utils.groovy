import groovy.transform.Field
import javax.swing.SwingUtilities

@Field static final DOC_TARGET_PAGE = 'page'
@Field static final DOC_TARGET_DIRECTORY = 'directory'

@Field private static final String CONFIG_NODE_NAME = 'config'
@Field private static final String DOC_DIR_PATH_KEY = 'docDirPath'
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

def static File pageFile(File docDir, String name) {
    return new File(docDir, name + '.md')
}

def static File assetsDir(File docDir, String name) {
    return new File(docDir, name + '.assets')
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
    if (node.link.node && node.link.node.link.file) {
        return node.link.node.link.file
    }
    return node.link.file ?: null
}

/**
 * DOC_TARGET_PAGE if the node links the existing file <docDir>/<text>.md,
 * DOC_TARGET_DIRECTORY if it links the existing directory <docDir>/<text>/, else null.
 */
def static getDocNodeType(node) {
    def linkedFile = getLinkedFile(node)
    if (!linkedFile) return null

    def docDir = loadDocDir(node)
    if (linkedFile == pageFile(docDir, node.text) && linkedFile.isFile()) return DOC_TARGET_PAGE
    if (linkedFile == new File(docDir, node.text) && linkedFile.isDirectory()) return DOC_TARGET_DIRECTORY
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
        } else if (getLinkedFile(n)) {
            indirectNodesByFile[normalizedFile(getLinkedFile(n))] = n
        }
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
        hit = nodesByFile[dir]
        if (hit) return hit

        if (dir.name.endsWith('.assets')) {
            hit = nodesByFile[pageFile(dir.parentFile, dir.name.replaceAll(/\.assets$/, ''))]
            if (hit) return hit
        }

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
    updateAllNextSteps(node)
    Thread.start { SearchIndex.updateIndex(docDir) }
}

/**
 * Syncs the "### Next Steps" list items of every page in the map into the page
 * node's children. Files are read on a background thread; the node mutations
 * are then applied in one batch on the Swing EDT, and only for pages whose
 * items actually differ from the current children, so an unchanged map stays
 * untouched (and unmodified).
 */
def static updateAllNextSteps(node) {
    def root = node.mindMap.root
    def docDir = loadDocDir(root)
    def targets = collectNodes(root)

    Thread.start {
        def nextSteps = [:]
        for (target in targets) {
            def lines = readNextSteps(target, docDir)
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

private static findChildByText(parentNode, String text) {
    return parentNode.children.find { it.text == text }
}

/**
 * The Next Steps items of a page node, or null for anything else: a node that
 * isn't a page, or one whose file can't be read (so one broken link doesn't
 * interrupt the scan of the rest of the map).
 */
private static List<String> readNextSteps(node, File docDir) {
    try {
        def pageFile = pageFile(docDir, node.text)
        if (getLinkedFile(node) != pageFile || !pageFile.isFile()) return null
        return pageFile.withReader('UTF-8') { reader ->
            skipToNextStepsHeading(reader)
            readNextStepLines(reader)
        }
    } catch (Exception ignored) {
        return null
    }
}

private static void skipToNextStepsHeading(Reader reader) {
    while (true) {
        String line = reader.readLine()
        if (line == null || line == NEXT_STEPS_HEADING) break
    }
}

private static List<String> readNextStepLines(Reader reader) {
    def lines = []
    while (true) {
        String line = reader.readLine()
        if (line == null || line.startsWith('#')) break

        if (isListItem(line)) {
            lines << line.substring(2)
            if (lines.size() == MAX_NEXT_STEPS) break
        }
    }
    return lines
}

private static boolean isListItem(String line) {
    return (line.startsWith('* ') || line.startsWith('- ')) && line.length() > MIN_LIST_ITEM_LENGTH
}

private static void applyNextSteps(node, List<String> lines) {
    for (child in node.getChildren()) {
        child.delete()
    }
    for (line in lines) {
        node.createChild().text = line
    }
}
