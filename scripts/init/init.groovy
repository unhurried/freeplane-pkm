import java.time.LocalDateTime

import org.freeplane.core.util.LogUtils
import org.freeplane.plugin.script.ScriptingEngine
import org.freeplane.plugin.script.ScriptingPermissions

// Runs at Freeplane startup: refreshes the Next Steps children and the search
// index at most every UPDATE_FREQUENCY_MIN minutes, whenever the map changes.

def maps = c.getOpenMindMaps()
if (maps.size() != 1) return
def map = maps.getFirst()

def UPDATE_FREQUENCY_MIN = 5

// Init scripts run with the global scripting permissions (Tools -> Preferences ->
// Scripting), which deny file access by default, not the per-script permissions
// the add-on declares for its menu scripts. Touching the pages directly from here
// throws a SecurityException (surfacing on the AWT thread as a misleading
// "Cannot format given Object as a Number"), so the update runs as a nested
// script granted read and write access explicitly.
def UPDATE_SCRIPT = 'Utils.updateNextStepsAndIndex(node)'
def UPDATE_PERMISSIONS = new ScriptingPermissions([
    (ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING)          : true,
    (ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION) : true,
    (ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_WRITE_RESTRICTION): true,
])

def lastUpdated = LocalDateTime.now()

map.addListener({
    if (LocalDateTime.now().isBefore(lastUpdated.plusMinutes(UPDATE_FREQUENCY_MIN))) return

    // Recorded before the run, so a failing update is retried on the next interval
    // rather than on every node change.
    lastUpdated = LocalDateTime.now()
    try {
        // Does its file I/O in the background and returns immediately.
        ScriptingEngine.executeScript(map.root.delegate, UPDATE_SCRIPT, UPDATE_PERMISSIONS)
    } catch (Exception e) {
        // Never let this escape into the AWT event thread.
        LogUtils.warn('failed to run periodic update', e)
    }
})
