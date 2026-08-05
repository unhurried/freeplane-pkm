// Only re-read pages whose Markdown file changed since the last run.
// The previous run time is stored under the config node so unchanged
// pages are skipped, keeping the update fast on large maps.
Utils.updateAllNextSteps(node)
