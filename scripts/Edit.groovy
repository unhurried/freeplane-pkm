import javax.swing.JOptionPane
import org.freeplane.core.ui.components.UITools

def pageName = node.text
def pageDirPath = Utils.loadPageDirPath(c, ui)
def pageDir = new File(pageDirPath)
if (!pageDir.exists()) {
    ui.errorMessage('page directory is missimg.')
    return
}

def pageFile = new File(pageDir, pageName + '.md')
if (!pageFile.exists()) {
    ui.errorMessage('page file is missing.')
    return
}

java.awt.Desktop.getDesktop().open(pageFile)
UITools.informationMessage('Close this after editing the file finishes.')

Utils.updateNextSteps(pageFile, node)
