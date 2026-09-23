// @ExecutionModes({ON_SINGLE_NODE})
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableCellRenderer
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

// Search dialog for the document directory (see SearchIndex.groovy). A result
// can be opened in the OS, or - when a node in the map links it - that node
// selected and centered instead.

def UPDATING_INDEX_STATUS = 'Updating search index...'
// Combo box labels in display order; the first entry is the default.
def SORT_OPTIONS = ['Last modified (newest first)': SearchSort.MODIFIED, 'Relevance': SearchSort.RELEVANCE]
def SCOPE_OPTIONS = ['Pages': SearchScope.PAGES, 'Attachments': SearchScope.ASSETS, 'Pages and attachments': SearchScope.ALL]

def docDir
try {
    docDir = Utils.loadDocDir(node)
} catch (Exception e) {
    ui.errorMessage(e.message)
    return
}

// The dialog outlives this script, so node lookups are anchored to the map root.
def mapRoot = node.mindMap.root

// Owned by the Freeplane window, so selecting a node never hides the dialog behind the map.
Frame owner = null
try {
    owner = (Frame) ui.frame
} catch (Exception ignored) {
    // Fall back to the default (unowned) dialog below.
}

// Identifies this map's dialog for reuse (below). Keyed on the map model's
// identity, not its path: a reopened map is a new model whose old dialog holds
// nodes that are no longer selectable.
def mapId
try {
    mapId = System.identityHashCode(node.mindMap.delegate)
} catch (Exception ignored) {
    mapId = ''
}
def dialogName = "freeplane-pkm-search|${mapId}|${docDir.absolutePath}".toString()

// Each menu script invocation gets its own class loader, so a second dialog
// would reload Lucene and Kuromoji's dictionary while the first copy stays on
// the heap. Re-show the existing dialog instead; componentShown (below) does
// the index refresh and focus handling on every show.
def existingDialog = null
try {
    existingDialog = owner?.ownedWindows?.find { window ->
        window instanceof JDialog && window.name == dialogName && window.displayable
    }
} catch (Exception ignored) {
    // No reusable dialog - build a new one below.
}
if (existingDialog) {
    existingDialog.visible = true
    existingDialog.toFront()
    return
}

def columnNames = ['File', 'Modified', 'Node', 'Snippet'] as String[]
def columnWidths = [200, 110, 180, 320]
def MODIFIED_COLUMN = columnNames.findIndexOf { it == 'Modified' }
def tableModel = new DefaultTableModel(columnNames, 0) {
    boolean isCellEditable(int row, int col) { false }

    // Dates, so the column sorter orders them chronologically whatever the display format.
    Class getColumnClass(int col) { col == MODIFIED_COLUMN ? Date : String }
}
def currentHits = []
def currentNodes = []

def keywordField = new JTextField(30)
def searchButton = new JButton('Search')
def sortCombo = new JComboBox(SORT_OPTIONS.keySet() as String[])
def scopeCombo = new JComboBox(SCOPE_OPTIONS.keySet() as String[])
def statusLabel = new JLabel(' ')
def openFileButton = new JButton('Open File')
def selectNodeButton = new JButton('Select Node')
def table = new JTable(tableModel)
table.rowHeight = 22
table.autoCreateRowSorter = true
columnWidths.eachWithIndex { width, i -> table.columnModel.getColumn(i).preferredWidth = width }
def modifiedPattern = Utils.DATE_FORMAT + ' HH:mm'
table.columnModel.getColumn(MODIFIED_COLUMN).cellRenderer = new DefaultTableCellRenderer() {
    protected void setValue(Object value) { super.setValue(value ? ((Date) value).format(modifiedPattern) : '') }
}

def updateActionButtons = {
    def viewRow = table.selectedRow
    def hasSelection = viewRow >= 0
    openFileButton.enabled = hasSelection
    selectNodeButton.enabled = hasSelection && viewRow < currentNodes.size() &&
            currentNodes[table.convertRowIndexToModel(viewRow)] != null
}

// Nodes and paths are resolved on the search thread (see runSearch); this only fills the table.
def showHits = { List hits, List nodes, List nodePaths ->
    currentHits = hits
    currentNodes = nodes

    tableModel.rowCount = 0
    hits.eachWithIndex { hit, i ->
        tableModel.addRow([hit.relativePath, new Date(hit.lastModified), nodePaths[i], hit.snippet] as Object[])
    }
    statusLabel.text = hits.isEmpty() ? 'No results' : "${hits.size()} result(s)"
    updateActionButtons()
}

// Off while a search runs, so changing sort/scope can't start overlapping searches.
def setSearchControlsEnabled = { boolean enabled ->
    [searchButton, sortCombo, scopeCombo].each { it.enabled = enabled }
}

def runSearch = {
    def keywordText = keywordField.text
    def sort = SORT_OPTIONS[sortCombo.selectedItem]
    def scope = SCOPE_OPTIONS[scopeCombo.selectedItem]
    setSearchControlsEnabled(false)
    statusLabel.text = 'Searching...'
    Thread.start {
        List hits
        try {
            hits = SearchIndex.search(docDir, keywordText, sort, scope)
        } catch (Exception e) {
            hits = []
            SwingUtilities.invokeLater { statusLabel.text = "search failed: ${e.message}" }
        }

        // Resolving hits to nodes walks the whole map and normalizes every linked
        // file, so it stays off the EDT. Rebuilt per search so results reflect
        // links changed since the dialog opened. Reads only; mutations go via EDT.
        def nodes
        try {
            def nodesByFile = Utils.collectNodesByLinkedFile(mapRoot)
            nodes = hits.collect { hit -> Utils.findNodeForFile(nodesByFile, hit.file, docDir) }
        } catch (Exception ignored) {
            // A hit whose node can't be resolved (map changed underneath) is still worth showing as a file.
            nodes = hits.collect { null }
        }
        def nodePaths = nodes.collect { targetNode -> targetNode ? Utils.nodePathText(targetNode) : '' }

        SwingUtilities.invokeLater {
            showHits(hits, nodes, nodePaths)
            setSearchControlsEnabled(true)
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
// Re-run with the new order/filter, unless there is nothing to search for yet.
def rerunSearch = { if (keywordField.text.trim()) runSearch() }
sortCombo.addActionListener { rerunSearch() }
scopeCombo.addActionListener { rerunSearch() }
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
def searchOptionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0))
searchOptionsPanel.add(new JLabel('Show:'))
searchOptionsPanel.add(scopeCombo)
searchOptionsPanel.add(new JLabel('Sort:'))
searchOptionsPanel.add(sortCombo)
searchOptionsPanel.add(searchButton)
searchPanel.add(searchOptionsPanel, BorderLayout.EAST)

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

// Brings the index up to date in the background; searching meanwhile works
// against what is on disk. warmUp() loads the Kuromoji dictionary here rather
// than on the user's first query.
def indexRefreshInProgress = new AtomicBoolean(false)
def refreshIndex = {
    if (!indexRefreshInProgress.compareAndSet(false, true)) return
    statusLabel.text = UPDATING_INDEX_STATUS
    Thread.start {
        try {
            SearchIndex.warmUp()
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
// Hidden, not disposed, and named, so the next F6 re-shows this dialog (see the top of this script).
dialog.defaultCloseOperation = JDialog.HIDE_ON_CLOSE
dialog.name = dialogName
dialog.contentPane = content
dialog.size = new Dimension(960, 540)
dialog.setLocationRelativeTo(owner)
// Fires on the first show and on every reopen.
dialog.addComponentListener(new ComponentAdapter() {
    void componentShown(ComponentEvent event) {
        // Queued: the window can't take focus while it is still being shown.
        SwingUtilities.invokeLater {
            keywordField.requestFocusInWindow()
            keywordField.selectAll()
        }
        refreshIndex()
    }
})
updateActionButtons()
dialog.visible = true
