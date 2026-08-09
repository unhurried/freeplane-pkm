import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

// A dedicated Swing search dialog for the document directory: full-text,
// case-insensitive, AND-of-keywords search across both file content and file
// name (see SearchIndex.groovy), with results opened directly via
// Desktop.open() - not turned into mind map nodes.

def docDir
try {
    docDir = Utils.loadDocDir(node)
} catch (Exception e) {
    ui.errorMessage(e.message)
    return
}

def columnNames = ['File', 'Snippet'] as String[]
def tableModel = new DefaultTableModel(columnNames, 0) {
    boolean isCellEditable(int row, int col) { false }
}
def currentHits = []

def keywordField = new JTextField(30)
def searchButton = new JButton('Search')
def statusLabel = new JLabel(' ')
def table = new JTable(tableModel)
table.rowHeight = 22
table.autoCreateRowSorter = true
table.columnModel.getColumn(0).preferredWidth = 220
table.columnModel.getColumn(1).preferredWidth = 480

def showHits = { List hits ->
    currentHits = hits
    tableModel.rowCount = 0
    hits.each { hit -> tableModel.addRow([hit.relativePath, hit.snippet] as Object[]) }
    statusLabel.text = hits.isEmpty() ? 'No results' : "${hits.size()} result(s)"
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

searchButton.addActionListener { runSearch() }
keywordField.addActionListener { runSearch() }
table.addMouseListener(new MouseAdapter() {
    void mouseClicked(MouseEvent e) {
        if (e.clickCount == 2) openSelectedHit()
    }
})
table.addKeyListener(new KeyAdapter() {
    void keyPressed(KeyEvent e) {
        if (e.keyCode == KeyEvent.VK_ENTER) openSelectedHit()
    }
})

def searchPanel = new JPanel(new BorderLayout(4, 4))
searchPanel.add(new JLabel('Keyword: '), BorderLayout.WEST)
searchPanel.add(keywordField, BorderLayout.CENTER)
searchPanel.add(searchButton, BorderLayout.EAST)

def content = new JPanel(new BorderLayout(4, 4))
content.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
content.add(searchPanel, BorderLayout.NORTH)
content.add(new JScrollPane(table), BorderLayout.CENTER)
content.add(statusLabel, BorderLayout.SOUTH)

def dialog = new JDialog((Frame) null, 'Search', false)
dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
dialog.contentPane = content
dialog.size = new Dimension(700, 450)
dialog.setLocationRelativeTo(null)
dialog.visible = true
keywordField.requestFocusInWindow()

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
