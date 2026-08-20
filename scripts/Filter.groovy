import java.time.LocalDate

def FILTER_HELP_MESSAGE = """\
Command:
  [a] show all
  [t] show todo list
  [d] due today or overdue
  (empty) hide archive"""

def keyword = ui.showInputDialog(node.delegate, FILTER_HELP_MESSAGE, null)

def isUnderArchive = { n ->
    def current = n
    while (current != null) {
        if (current.text.equalsIgnoreCase('archive')) return true
        current = current.getParent()
    }
    false
}

if (keyword == null || keyword.isEmpty()) {
    node.map.filter() { !isUnderArchive(it) }
} else if (keyword == 'a') {
    node.map.filter(true, true) { true }
} else if (keyword == 't') {
    node.map.filter(true, true) {
        it.getParent() != null && it.getParent().getParent() == null && it.text.equals('ToDo')
    }
} else if (keyword == 'd') {
    def today = LocalDate.now()
    node.map.filter(true, true) {
        def dueDateStr = it.text.find(/^\d\d\/\d\d\/\d\d/)
        if (dueDateStr == null) return false
        def dueDate = LocalDate.parse(dueDateStr, 'yy/MM/dd')
        return !today.isBefore(dueDate)
    }
} else {
    def lowerKeyword = keyword.toLowerCase()
    node.map.filter(true, true) { it.text.toLowerCase().contains(lowerKeyword) }
}
