// Manual trigger for the refresh the startup listener (init.groovy) runs periodically.
try {
    Utils.updateNextStepsAndIndex(node)
} catch (Exception e) {
    ui.errorMessage(e.message)
}
