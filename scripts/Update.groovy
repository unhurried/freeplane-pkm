// Refreshes both pieces of derived state that the periodic background
// listener also maintains (see init.groovy): each page's "### Next Steps"
// children, synced from its Markdown file, and the full-text search index.
// This is just a manual trigger for the same incremental updates, for when
// a user doesn't want to wait for the periodic refresh.
def docDir
try {
    docDir = Utils.loadDocDir(node)
} catch (Exception e) {
    ui.errorMessage(e.message)
    return
}

// Utils.updateAllNextSteps() backgrounds its own file reads. SearchIndex.updateIndex()
// does not, so it is wrapped in Thread.start here for the same reason: this script
// runs on the EDT and file I/O must not block it.
Utils.updateAllNextSteps(node)
Thread.start {
    SearchIndex.updateIndex(docDir)
}
