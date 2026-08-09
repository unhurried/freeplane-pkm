def docDir
try {
    docDir = Utils.loadDocDir(node)
} catch (Exception e) {
    ui.errorMessage(e.message)
    return
}

// Only (re-)extracts files that are new or changed since the last run (see
// SearchIndex.updateIndex); this is a manual trigger for that same
// incremental update, for when a user doesn't want to wait for the periodic
// background refresh (see init.groovy). Runs on a background thread since
// indexing does file I/O and can be slow on a large document directory.
Thread.start {
    SearchIndex.updateIndex(docDir)
}
