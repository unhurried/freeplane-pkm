import groovy.transform.Field
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

@Field static final DOC_TARGET_PAGE = 'page'
@Field static final DOC_TARGET_DIRECTORY = 'directory'

@Field private static final NEXT_STEPS_HEADING = '### Next Steps'
@Field private static final MAX_NEXT_STEPS = 3
// "* " / "- " (2 chars) plus at least 2 more, so a marker with no more than a
// single trailing character (e.g. "* x") isn't treated as a real list item.
@Field private static final MIN_LIST_ITEM_LENGTH = 3
@Field private static final CONFIG_NODE_NAME = 'config'
@Field private static final DOC_DIR_PATH_KEYS = ['docDirPath', 'pageDirPath']
@Field private static final LAST_UPDATED_KEY = 'nextStepsUpdatedAt'

// Guards updateAllNextSteps() against overlapping scans (e.g. the periodic
// listener firing again before a previous scan of a large map has finished).
// Held from the start of the background scan until its results have been
// applied on the EDT.
@Field private static final AtomicBoolean NEXT_STEPS_SCAN_IN_PROGRESS = new AtomicBoolean(false)

/**
 * Loads the document directory path from the mind map's config node.
 * Config structure: root > config > docDirPath > [path value]
 */
def static loadDocDir(node) {
    def configNode = findChildByText(node.mindMap.root, CONFIG_NODE_NAME)
    if (!configNode) throw new RuntimeException('config node is missing.')

    def docDirPath = loadDocDirPath(configNode)
    if (!docDirPath) throw new RuntimeException('docDirPath node is missing.')

    def docDir = new File(docDirPath)
    if (!docDir.exists()) throw new RuntimeException('document directory is missing.')

    return docDir
}

/**
 * Returns the file linked from the node (directly or via an intermediate node link).
 */
def static getLinkedFile(node) {
    if (node.link.node && node.link.node.link.file) {
        return node.link.node.link.file
    }
    return node.link.file ?: null
}

/**
 * Determines whether the node represents a page (its link is the existing
 * file <docDir>/<text>.md) or a directory (the existing directory
 * <docDir>/<text>/). Returns DOC_TARGET_PAGE, DOC_TARGET_DIRECTORY, or null.
 */
def static getDocNodeType(node) {
    def linkedFile = getLinkedFile(node)
    if (!linkedFile) return null

    def docDir = loadDocDir(node)
    if (linkedFile == pageFileOf(node, docDir) && linkedFile.isFile()) return DOC_TARGET_PAGE
    if (linkedFile == new File(docDir, node.text) && linkedFile.isDirectory()) return DOC_TARGET_DIRECTORY
    return null
}

/**
 * Refreshes the Next Steps of every node of the node's map and records the
 * run time under the config node, so the next run can skip pages whose
 * Markdown file has not been modified since.
 *
 * Deciding which pages changed (stat'ing every linked file) and reading their
 * content both happen on a single background thread, so a map with many
 * nodes doesn't perform per-node file I/O synchronously on the UI thread.
 * Node mutations - updating children and recording the run time - are
 * collected while scanning and applied afterwards in one batch on the Swing
 * EDT, since the node model is only safe to mutate there; batching also means
 * a single map refresh covers the whole run instead of one per changed page.
 *
 * Overlapping runs collapse into a no-op: if a scan triggered earlier (e.g.
 * by the periodic listener) hasn't finished yet, this call returns
 * immediately instead of starting a second concurrent scan.
 */
def static updateAllNextSteps(node) {
    def root = node.mindMap.root

    // A single, cheap directory check - resolved eagerly and synchronously so
    // a missing/misconfigured docDir still fails fast and visibly to callers
    // such as the manual "Update Next Steps and Search Index" command. Resolving it once here
    // (instead of per node, as getDocNodeType()/loadDocDir() would) also avoids
    // re-walking the config node and re-stat'ing docDir for every node below.
    def docDir = Utils.loadDocDir(root)

    if (!NEXT_STEPS_SCAN_IN_PROGRESS.compareAndSet(false, true)) return

    def sinceMillis = loadNextStepsUpdatedAt(root)
    def startedAt = System.currentTimeMillis()
    // Collected up front (cheap, no file I/O) since it just walks the node
    // tree; the actual per-node file checks happen on the background thread.
    def targets = collectNodes(root)

    Thread.start {
        def updates
        try {
            updates = [:]
            for (target in targets) {
                def lines = readChangedNextSteps(target, docDir, sinceMillis)
                if (lines != null) updates[target] = lines
            }
        } catch (Exception e) {
            NEXT_STEPS_SCAN_IN_PROGRESS.set(false)
            return
        }

        SwingUtilities.invokeLater {
            try {
                updates.each { target, lines -> applyNextSteps(target, lines) }
                saveNextStepsUpdatedAt(root, startedAt)
            } finally {
                NEXT_STEPS_SCAN_IN_PROGRESS.set(false)
            }
        }
    }
}

/**
 * Reads the last time the Next Steps were refreshed from the map's config node.
 * Config structure: root > config > nextStepsUpdatedAt > [epoch millis value]
 * Returns 0 when the timestamp has never been recorded.
 */
def static loadNextStepsUpdatedAt(node) {
    def configNode = findChildByText(node.mindMap.root, CONFIG_NODE_NAME)
    if (!configNode) return 0L

    def keyNode = findChildByText(configNode, LAST_UPDATED_KEY)
    if (!keyNode) return 0L

    def valueNode = keyNode.children[0]
    if (!valueNode) return 0L

    try {
        return Long.parseLong(valueNode.plainText.trim())
    } catch (NumberFormatException ignored) {
        return 0L
    }
}

/**
 * Records the last time the Next Steps were refreshed under the map's config node,
 * creating the config child nodes on demand.
 */
def static saveNextStepsUpdatedAt(node, long millis) {
    def configNode = findChildByText(node.mindMap.root, CONFIG_NODE_NAME)
    if (!configNode) throw new RuntimeException('config node is missing.')

    def keyNode = findChildByText(configNode, LAST_UPDATED_KEY)
    if (!keyNode) {
        keyNode = configNode.createChild()
        keyNode.text = LAST_UPDATED_KEY
    }

    def valueNode = keyNode.children[0] ?: keyNode.createChild()
    valueNode.text = String.valueOf(millis)
}

/**
 * Maps every file linked from the subtree rooted at the given node to the
 * node that links it, keyed by normalized File (see normalizedFile()). Used
 * both to list which files the map links at all (the key set) and to resolve
 * a search hit (an arbitrary file under the document directory) back to the
 * mind map node a user would want selected - see findNodeForFile().
 *
 * A node whose link points directly at a file (node.link.file) always wins
 * over one that only links it indirectly, through another node
 * (node.link.node.link.file - the other form getLinkedFile() also follows),
 * so an intermediate "see also"-style link never shadows the node that
 * actually represents the document.
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
 * Resolves a file (e.g. a full-text search hit) to the mind map node that
 * represents it, using a file->node map built by collectNodesByLinkedFile().
 * A search hit is not always a file linked directly from a node; it may be:
 *  - a page or directory file itself (direct match)
 *  - a file inside a linked directory (walking up from the file to docDir,
 *    the first ancestor directory that matches a directory node is used)
 *  - a file under a page's "<page>.assets/" attachments directory (the
 *    sibling "<page>.md" page node is used instead, per the page/assets
 *    convention described in this file's class-level docs)
 * Returns null if no node in the map links the file, any of its ancestor
 * directories, or (for an assets file) its page.
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
            def pageFile = new File(dir.parentFile, dir.name.replaceAll(/\.assets$/, '') + '.md')
            hit = nodesByFile[pageFile]
            if (hit) return hit
        }

        if (dir == normalizedDocDir) break
        dir = dir.parentFile
    }
    return null
}

/**
 * The form every file is compared in: absolute, with "." and ".." segments
 * folded away. Pure path arithmetic - unlike File.getCanonicalFile(), which
 * hits the filesystem on every call - so comparing thousands of linked files
 * per search costs nothing. Symlinks are deliberately not resolved.
 */
def static File normalizedFile(File file) {
    return file.toPath().toAbsolutePath().normalize().toFile()
}

/**
 * Builds a human-readable "root > ... > text" path for a node, walking up
 * through getParent(). Used by Search.groovy to show a result's position in
 * the map without requiring it to be selected first.
 */
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
 * Opens a file or directory in the OS's default application.
 *
 * A thin wrapper over java.awt.Desktop, kept here rather than called inline from
 * the scripts so that the scripts stay drivable from a test: Desktop is both
 * unavailable in a headless JVM and genuinely side-effecting (it spawns an
 * external application), so tests replace this method instead.
 */
def static openInDesktop(File file) {
    java.awt.Desktop.getDesktop().open(file)
}

// --- Private helper methods ---

private static File pageFileOf(node, File docDir) {
    return new File(docDir, node.text + '.md')
}

/** Calls action for the node and, depth-first, every node below it. */
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

private static String loadDocDirPath(configNode) {
    for (child in configNode.children) {
        if (child.text in DOC_DIR_PATH_KEYS && child.children[0]) {
            return child.children[0].plainText
        }
    }
    return null
}

private static void skipToNextStepsHeading(Reader reader) {
    while (true) {
        String line = reader.readLine()
        if (line == null || line == NEXT_STEPS_HEADING) break
    }
}

private static List<String> readNextStepLinesFromFile(File pageFile) {
    def lines
    pageFile.withReader { reader ->
        skipToNextStepsHeading(reader)
        lines = readNextStepLines(reader)
    }
    return lines
}

/**
 * The Next Steps lines of a page node whose file changed after sinceMillis,
 * or null for anything else: a node that isn't a page, an unchanged page, or
 * an unreadable one - the last returned rather than thrown, so one broken
 * link doesn't interrupt scanning the rest of the map. Takes the
 * already-resolved docDir instead of re-deriving it per node.
 */
private static List<String> readChangedNextSteps(node, File docDir, long sinceMillis) {
    try {
        def pageFile = pageFileOf(node, docDir)
        if (getLinkedFile(node) != pageFile || !pageFile.isFile()) return null
        if (sinceMillis > 0 && pageFile.lastModified() <= sinceMillis) return null
        return readNextStepLinesFromFile(pageFile)
    } catch (Exception ignored) {
        return null
    }
}

private static void clearChildren(node) {
    for (child in node.getChildren()) {
        child.delete()
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

private static void applyNextSteps(node, List<String> lines) {
    clearChildren(node)
    for (line in lines) {
        def newNode = node.createChild()
        newNode.text = line
    }
}

private static boolean isListItem(String line) {
    return (line.startsWith('* ') || line.startsWith('- ')) && line.length() > MIN_LIST_ITEM_LENGTH
}
