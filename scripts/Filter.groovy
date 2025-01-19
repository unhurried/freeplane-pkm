import java.time.LocalDate

keyword = ui.showInputDialog(node.delegate, "Command:\n  [t] due today\n  [b] behind deadline\n  (other) keyword search\n  (empty) clear filter", null)

if (keyword == null || keyword.isEmpty()) {
    node.map.filter(true, true){ true }
} else if (keyword == 't') {
    today = LocalDate.now().format('yy/MM/dd')
    node.map.filter(true, true){ it.text.startsWith(today)}
} else if (keyword == 'b') {
    today = LocalDate.now()
    node.map.filter(true, true){ 
        dueDateStr = it.text.find(/^\d\d\/\d\d\/\d\d/)
        if (dueDateStr == null) return false
        dueDate = LocalDate.parse(dueDateStr, 'yy/MM/dd')
        return today.isAfter(dueDate)
    }
} else {
    node.map.filter(true, true){ it.text.toLowerCase().contains(keyword) }
}
