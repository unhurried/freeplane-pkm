import java.time.LocalDateTime

def maps = c.getOpenMindMaps()
if (maps.size() != 1) return
def map = maps.getFirst()

def UPDATE_FREQUENCY_MIN = 5
def updating = false
def lastUpdated = LocalDateTime.now()

map.addListener({
    def updateAfter = lastUpdated.plusMinutes(UPDATE_FREQUENCY_MIN)
    if (LocalDateTime.now().isBefore(updateAfter)) return
    if (updating) return

    updating = true
    try {
        def sinceMillis = Utils.loadNextStepsUpdatedAt(map.root)
        def startedAt = System.currentTimeMillis()
        c.findAll().each {
            Utils.updateNextSteps(it, sinceMillis)
        }
        Utils.saveNextStepsUpdatedAt(map.root, startedAt)
        lastUpdated = LocalDateTime.now()
    } finally {
        updating = false
    }
})
