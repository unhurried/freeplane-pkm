import java.time.LocalDateTime;  

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
    c.findAll().each {
        Utils.updateNextSteps(it)
    }

    lastUpdated = LocalDateTime.now()
    updating = false
})
