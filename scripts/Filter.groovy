keyword = ui.showInputDialog(node.delegate, "Search Keyword", null)

if (keyword == null || keyword.isEmpty()) {
    node.map.filter(true, true){ true }
} else {
    node.map.filter(true, true){ it.text.toLowerCase().contains(keyword) }
}
