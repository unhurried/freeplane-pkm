// Only re-read pages whose Markdown file changed since the last run.
// The previous run time is stored under the config node so unchanged
// pages are skipped, keeping the update fast on large maps.
def sinceMillis = Utils.loadNextStepsUpdatedAt(node)
def startedAt = System.currentTimeMillis()

c.findAll().each {
    Utils.updateNextSteps(it, sinceMillis)
}

Utils.saveNextStepsUpdatedAt(node, startedAt)
