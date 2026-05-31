import java.time.LocalDate

def FILTER_HELP_MESSAGE = """\
Command:
  [t] show todo list
  [d] due today or overdue
  (empty) clear filter"""

def keyword = ui.showInputDialog(node.delegate, FILTER_HELP_MESSAGE, null)

if (keyword == null || keyword.isEmpty()) {
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
    node.map.filter(true, true) { it.text.toLowerCase().contains(keyword) }
}
