// Equivalent of the built-in MindMap/EditAction (default shortcut F2).
// The built-in action cannot be shipped with an add-on shortcut, so this script
// delegates to it, keeping the behavior identical.

import org.freeplane.features.mode.Controller
import java.awt.event.ActionEvent

def key = 'EditAction'
def action = Controller.currentModeController.getAction(key)
if (action == null) {
    ui.errorMessage("Action not found: $key")
    return
}
action.actionPerformed(new ActionEvent(node.delegate, ActionEvent.ACTION_PERFORMED, key))
