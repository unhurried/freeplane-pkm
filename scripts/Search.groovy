import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean

// A dedicated Swing search dialog for the document directory: full-text,
// case-insensitive, AND-of-keywords search across both file content and file
// name (see SearchIndex.groovy). Each result can either be opened directly
// via Utils.openInDesktop(), or - when it corresponds to a page/directory node
// somewhere in the map - have that node selected and centered instead, so a
// search result can be used as a shortcut back into the map's own structure.

def UPDATING_INDEX_STATUS = 'Updating search index...'

def docDir
try {
    docDir = Utils.loadDocDir(node)
} catch (Exception e) {
    ui.errorMessage(e.message)
    return
}

// The dialog is modeless and its callbacks keep running after this script
// returns, so the map root - not the node the script was invoked on - is
// what all later node lookups/selections are anchored to.
def mapRoot = node.mindMap.root

// Owned by the main Freeplane window when available, so the dialog can never
// end up hidden behind the map after a node selection brings the map's own
// window forward; falls back to an unowned dialog if ui.frame isn't usable.
Frame owner = null
try {
    owner = (Frame) ui.frame
} catch (Exception ignored) {
    // Fall back to the default (unowned) dialog below.
}

// Identifies the dialog belonging to this map and document directory, so a
// second map's Search window is never mistaken for this one's. Keyed on the
// identity of the map model rather than on its file path, because a map that
// was closed and reopened is a new model: its dialog still holds nodes from
// the old one, which are no longer selectable, so it must not be reused.
def mapId
try {
    mapId = System.identityHashCode(node.mindMap.delegate)
} catch (Exception ignored) {
    mapId = ''
}
def dialogName = "freeplane-pkm-search|${mapId}|${docDir.absolutePath}".toString()

// Freeplane compiles and runs every menu script invocation with its own script
// class loader over the add-on's lib jar, so opening a second dialog would
// build a second copy of the entire search stack - Lucene, and Kuromoji's
// bundled dictionary above all - paying its class loading and dictionary load
// again while the previous copy stays on the heap. Re-showing the window that
// is already open reuses the copy that is already warm instead, which is what
// keeps a reopened dialog's first search as fast as its second one; this
// script then only resolves the document directory and hands the window back.
def existingDialog = null
try {
    existingDialog = owner?.ownedWindows?.find { window ->
        window instanceof JDialog && window.name == dialogName && window.displayable
    }
} catch (Exception ignored) {
    // No reusable dialog - build a new one below.
}
if (existingDialog) {
    // Re-showing fires componentShown on the existing dialog, which refreshes
    // the index and restores focus from its own (warm) class loader.
    existingDialog.visible = true
    existingDialog.toFront()
    return
}

def columnNames = ['File', 'Node', 'Snippet'] as String[]
def tableModel = new DefaultTableModel(columnNames, 0) {
    boolean isCellEditable(int row, int col) { false }
}
def currentHits = []
def currentNodes = []

def keywordField = new JTextField(30)
def searchButton = new JButton('Search')
def statusLabel = new JLabel(' ')
def openFileButton = new JButton('Open File')
def selectNodeButton = new JButton('Select Node')
def table = new JTable(tableModel)
table.rowHeight = 22
table.autoCreateRowSorter = true
table.columnModel.getColumn(0).preferredWidth = 200
table.columnModel.getColumn(1).preferredWidth = 180
table.columnModel.getColumn(2).preferredWidth = 320

def updateActionButtons = {
    def viewRow = table.selectedRow
    def hasSelection = viewRow >= 0
    openFileButton.enabled = hasSelection
    selectNodeButton.enabled = hasSelection && viewRow < currentNodes.size() &&
            currentNodes[table.convertRowIndexToModel(viewRow)] != null
}

// Takes the resolved nodes and their map paths as arguments rather than
// working them out here: both are derived on the background thread that ran
// the search (see runSearch), leaving this - the part that has to run on the
// EDT - to nothing but filling the table.
def showHits = { List hits, List nodes, List nodePaths ->
    currentHits = hits
    currentNodes = nodes

    tableModel.rowCount = 0
    hits.eachWithIndex { hit, i ->
        tableModel.addRow([hit.relativePath, nodePaths[i], hit.snippet] as Object[])
    }
    statusLabel.text = hits.isEmpty() ? 'No results' : "${hits.size()} result(s)"
    updateActionButtons()
}

def runSearch = {
    def keywordText = keywordField.text
    searchButton.enabled = false
    statusLabel.text = 'Searching...'
    Thread.start {
        List hits
        try {
            hits = SearchIndex.search(docDir, keywordText)
        } catch (Exception e) {
            hits = []
            SwingUtilities.invokeLater { statusLabel.text = "search failed: ${e.message}" }
        }

        // Resolving the hits to nodes walks the whole map, canonicalizes every
        // file it links and then builds a "root > ... > node" path per hit.
        // That stays on this thread instead of running on the EDT together
        // with the table update: on a large map - or one whose document
        // directory lives on a network/cloud-synced drive - it is easily long
        // enough to freeze the window on every single search. Only reads of
        // the node model happen here; mutations still go through the EDT.
        def results = hits
        def nodes
        def paths
        try {
            // Rebuilt on every search (not just once) so results reflect any node
            // links added, removed or retargeted since the dialog was opened.
            def nodesByFile = Utils.collectNodesByLinkedFile(mapRoot)
            nodes = results.collect { hit -> Utils.findNodeForFile(nodesByFile, hit.file, docDir) }
            paths = nodes.collect { targetNode -> targetNode ? Utils.nodePathText(targetNode) : '' }
        } catch (Exception ignored) {
            // A hit whose node can't be resolved - e.g. because the map changed
            // underneath this scan - is still worth showing as a file.
            nodes = results.collect { null }
            paths = results.collect { '' }
        }
        def targetNodes = nodes
        def nodePaths = paths

        SwingUtilities.invokeLater {
            showHits(results, targetNodes, nodePaths)
            searchButton.enabled = true
        }
    }
}

def selectRowAt = { int viewRow ->
    if (viewRow < 0) return
    if (table.selectedRow != viewRow) {
        table.setRowSelectionInterval(viewRow, viewRow)
    }
}

def openSelectedHit = {
    def viewRow = table.selectedRow
    if (viewRow < 0) return
    def hit = currentHits[table.convertRowIndexToModel(viewRow)]
    if (!hit.file.exists()) {
        statusLabel.text = "file no longer exists: ${hit.relativePath}"
        return
    }
    Utils.openInDesktop(hit.file)
}

def selectHitNode = {
    def viewRow = table.selectedRow
    if (viewRow < 0) return
    def modelRow = table.convertRowIndexToModel(viewRow)
    def targetNode = currentNodes[modelRow]
    if (!targetNode) {
        statusLabel.text = "no node links to this file: ${currentHits[modelRow].relativePath}"
        return
    }
    try {
        c.select(targetNode)
        c.centerOnNode(targetNode)
        statusLabel.text = "selected: ${Utils.nodePathText(targetNode)}"
    } catch (Exception e) {
        statusLabel.text = "failed to select node: ${e.message}"
    }
}

searchButton.addActionListener { runSearch() }
keywordField.addActionListener { runSearch() }
openFileButton.addActionListener { openSelectedHit() }
selectNodeButton.addActionListener { selectHitNode() }
table.selectionModel.addListSelectionListener { updateActionButtons() }

table.addMouseListener(new MouseAdapter() {
    void mouseClicked(MouseEvent e) {
        if (e.clickCount == 2) openSelectedHit()
    }

    void mousePressed(MouseEvent e) { maybeShowPopup(e) }

    void mouseReleased(MouseEvent e) { maybeShowPopup(e) }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return
        def viewRow = table.rowAtPoint(e.point)
        selectRowAt(viewRow)
        if (viewRow < 0) return

        def modelRow = table.convertRowIndexToModel(viewRow)
        def popup = new JPopupMenu()
        def openItem = new JMenuItem('Open File')
        openItem.addActionListener { openSelectedHit() }
        popup.add(openItem)
        def selectItem = new JMenuItem('Select Node')
        selectItem.enabled = currentNodes[modelRow] != null
        selectItem.addActionListener { selectHitNode() }
        popup.add(selectItem)
        popup.show(table, e.x, e.y)
    }
})
table.addKeyListener(new KeyAdapter() {
    void keyPressed(KeyEvent e) {
        if (e.keyCode != KeyEvent.VK_ENTER) return
        if (e.isControlDown()) {
            selectHitNode()
        } else {
            openSelectedHit()
        }
    }
})

def searchPanel = new JPanel(new BorderLayout(4, 4))
searchPanel.add(new JLabel('Keyword: '), BorderLayout.WEST)
searchPanel.add(keywordField, BorderLayout.CENTER)
searchPanel.add(searchButton, BorderLayout.EAST)

def actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0))
actionButtonsPanel.add(openFileButton)
actionButtonsPanel.add(selectNodeButton)

def statusPanel = new JPanel(new BorderLayout(4, 4))
statusPanel.add(statusLabel, BorderLayout.WEST)
statusPanel.add(actionButtonsPanel, BorderLayout.EAST)

def content = new JPanel(new BorderLayout(4, 4))
content.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
content.add(searchPanel, BorderLayout.NORTH)
content.add(new JScrollPane(table), BorderLayout.CENTER)
content.add(statusPanel, BorderLayout.SOUTH)

// Brings the index up to date in the background, so results reflect files
// added/changed since the last periodic refresh (see init.groovy) without the
// user having to run "Update Next Steps and Search Index" first. Search still
// works against whatever is currently on disk while this runs, and simply may
// miss the most recent changes until it finishes.
//
// warmUp() runs first, deliberately: it does the one-off initialization (the
// analyzer with its Kuromoji dictionary, and the index reader) that a search
// would otherwise have to do *while competing with this very refresh* for it,
// which is what made the first query after opening the dialog several times
// slower than the next one.
def indexRefreshInProgress = new AtomicBoolean(false)
def refreshIndex = {
    if (!indexRefreshInProgress.compareAndSet(false, true)) return
    statusLabel.text = UPDATING_INDEX_STATUS
    Thread.start {
        try {
            SearchIndex.warmUp(docDir)
            SearchIndex.updateIndex(docDir)
        } catch (Exception ignored) {
            // Best effort - a failed background refresh should not block searching.
        } finally {
            indexRefreshInProgress.set(false)
        }
        SwingUtilities.invokeLater {
            if (statusLabel.text == UPDATING_INDEX_STATUS) statusLabel.text = 'Ready'
        }
    }
}

def dialog = new JDialog(owner, 'Search', false)
// Hidden rather than disposed on close, and tagged with a name, so the next
// F6 can find and re-show this very dialog instead of building a new one -
// see the reuse check at the top of this script.
dialog.defaultCloseOperation = JDialog.HIDE_ON_CLOSE
dialog.name = dialogName
dialog.contentPane = content
dialog.size = new Dimension(960, 540)
dialog.setLocationRelativeTo(owner)
// Fires on the first show and on every reopen, so a reused dialog refreshes
// its index and takes focus exactly like a freshly built one.
dialog.addComponentListener(new ComponentAdapter() {
    void componentShown(ComponentEvent event) {
        // Queued rather than called straight away: this fires while the window
        // is still being shown, at which point it can't take focus yet.
        SwingUtilities.invokeLater {
            keywordField.requestFocusInWindow()
            keywordField.selectAll()
        }
        refreshIndex()
    }
})
updateActionButtons()
dialog.visible = true
