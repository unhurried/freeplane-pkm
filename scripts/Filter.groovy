import java.time.LocalDate

String message = "Command:\n"
message += "  [t] show todo list\n"
message += "  [d] due today or overdue\n"
message += "  (empty) clear filter"

keyword = ui.showInputDialog(node.delegate, message, null)

if (keyword == null || keyword.isEmpty()) {
    node.map.filter(true, true){ true }
} else if (keyword == 't') {
    node.map.filter(true, true){ 
        it.getParent() != null && it.getParent().getParent() == null && it.text.equals("ToDo")
    }
} else if (keyword == 'd') {
    today = LocalDate.now()
    node.map.filter(true, true){ 
        dueDateStr = it.text.find(/^\d\d\/\d\d\/\d\d/)
        if (dueDateStr == null) return false
        dueDate = LocalDate.parse(dueDateStr, 'yy/MM/dd')
        return today.isEqual(dueDate) || today.isAfter(dueDate)
    }
} else {
    node.map.filter(true, true){ it.text.toLowerCase().contains(keyword) }
}
