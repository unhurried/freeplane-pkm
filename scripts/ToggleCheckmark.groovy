// Toggle Freeplane's checkmark icon ("button_ok") on the selected node.
def ICON = 'button_ok'
if (node.icons.icons.contains(ICON)) {
    node.icons.removeIcon(ICON)
} else {
    node.icons.addIcon(ICON)
}
