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

def updating = false
def lastUpdated = LocalDateTime.now()

map.addListener({
    def updateAfter = lastUpdated.plusMinutes(UPDATE_FREQUENCY_MIN)
    if (LocalDateTime.now().isBefore(updateAfter)) return
    if (updating) return

    updating = true
    // Recorded before the run so that a failing update is retried on the next
    // interval instead of on every single node change.
    lastUpdated = LocalDateTime.now()
    try {
        ScriptingEngine.executeScript(map.root.delegate, UPDATE_SCRIPT, UPDATE_PERMISSIONS)
    } catch (Exception e) {
        // Never let this escape into the AWT event thread.
        LogUtils.warn('failed to update next steps', e)
    } finally {
        updating = false
    }
})
