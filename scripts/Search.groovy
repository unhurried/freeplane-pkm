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
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

// A dedicated Swing search dialog for the document directory: full-text,
// case-insensitive, AND-of-keywords search across both file content and file
// name (see SearchIndex.groovy). Each result can either be opened directly
// via Desktop.open(), or - when it corresponds to a page/directory node
// somewhere in the map - have that node selected and centered instead, so a
// search result can be used as a shortcut back into the map's own structure.

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

// Builds "root > ... > text" for a node's ancestor chain, so the Node column
// shows a result's position in the map without requiring a selection first.
def nodePathText = { targetNode ->
    def parts = []
    def current = targetNode
    while (current != null) {
        parts.add(0, current.text)
        current = current.getParent()
    }
    return parts.join(' > ')
}

def updateActionButtons = {
    def viewRow = table.selectedRow
    def hasSelection = viewRow >= 0
    openFileButton.enabled = hasSelection
    selectNodeButton.enabled = hasSelection && viewRow < currentNodes.size() &&
            currentNodes[table.convertRowIndexToModel(viewRow)] != null
}

def showHits = { List hits ->
    currentHits = hits
    // Rebuilt on every search (not just once) so results reflect any node
    // links added, removed or retargeted since the dialog was opened.
    def nodesByFile = Utils.collectNodesByLinkedFile(mapRoot)
    currentNodes = hits.collect { hit -> Utils.findNodeForFile(nodesByFile, hit.file, docDir) }

    tableModel.rowCount = 0
    hits.eachWithIndex { hit, i ->
        def targetNode = currentNodes[i]
        tableModel.addRow([hit.relativePath, targetNode ? nodePathText(targetNode) : '', hit.snippet] as Object[])
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
        def results = hits
        SwingUtilities.invokeLater {
            showHits(results)
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
    Desktop.getDesktop().open(hit.file)
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
        statusLabel.text = "selected: ${nodePathText(targetNode)}"
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

// Owned by the main Freeplane window when available, so the dialog can never
// end up hidden behind the map after a node selection brings the map's own
// window forward; falls back to an unowned dialog if ui.frame isn't usable.
Frame owner = null
try {
    owner = (Frame) ui.frame
} catch (Exception ignored) {
    // Fall back to the default (unowned) dialog below.
}

def dialog = new JDialog(owner, 'Search', false)
dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
dialog.contentPane = content
dialog.size = new Dimension(960, 540)
dialog.setLocationRelativeTo(owner)
dialog.visible = true
keywordField.requestFocusInWindow()
updateActionButtons()

// Bring the index up to date in the background as soon as the dialog opens,
// so results reflect files added/changed since the last periodic refresh
// (see init.groovy) without the user having to run "Update Search Index"
// first. Search still works against whatever is currently on disk while
// this runs, and simply may miss the most recent changes until it finishes.
statusLabel.text = 'Updating search index...'
Thread.start {
    try {
        SearchIndex.updateIndex(docDir)
    } catch (Exception ignored) {
        // Best effort - a failed background refresh should not block searching.
    }
    SwingUtilities.invokeLater {
        if (statusLabel.text == 'Updating search index...') statusLabel.text = 'Ready'
    }
}
