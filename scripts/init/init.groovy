import java.time.LocalDateTime

import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.ScriptingEngine
import org.freeplane.plugin.script.ScriptingPermissions

def maps = c.getOpenMindMaps()
if (maps.size() != 1) return
def map = maps.getFirst()

def UPDATE_FREQUENCY_MIN = 5

// Init scripts (<userdir>/scripts/init) are executed with the *global* scripting
// permissions of Tools -> Preferences -> Scripting, not with the per-script
// permissions the add-on declares for its menu scripts. Reading files is not
// permitted there by default, so touching the Markdown pages directly from this
// listener throws a SecurityException - which Freeplane in turn fails to format,
// surfacing on the AWT event thread as the misleading
// "java.lang.IllegalArgumentException: Cannot format given Object as a Number".
//
// The update is therefore not run inline but as a nested script that is granted
// read access explicitly, just like the add-on's menu scripts are.
def UPDATE_SCRIPT = 'Utils.updateAllNextSteps(node)'
def UPDATE_PERMISSIONS = new ScriptingPermissions([
    (ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING)         : true,
    (ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION): true,
])

def lastUpdated = LocalDateTime.now()

map.addListener({
    def updateAfter = lastUpdated.plusMinutes(UPDATE_FREQUENCY_MIN)
    if (LocalDateTime.now().isBefore(updateAfter)) return

    // Recorded before the run so that a failing update is retried on the next
    // interval instead of on every single node change.
    lastUpdated = LocalDateTime.now()
    try {
        // Utils.updateAllNextSteps() scans the map and reads changed pages on
        // a background thread and returns immediately, so this call does not
        // block the event thread. It also guards itself against overlapping
        // scans, so firing this again before a previous (slow) scan has
        // finished is safe and simply a no-op.
        ScriptingEngine.executeScript(map.root.delegate, UPDATE_SCRIPT, UPDATE_PERMISSIONS)
    } catch (Exception e) {
        // Never let this escape into the AWT event thread.
        LogUtils.warn('failed to update next steps', e)
    }
})
