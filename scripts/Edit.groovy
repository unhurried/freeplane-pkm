import javax.swing.JOptionPane
import org.freeplane.core.ui.components.UITools

def pageName = node.text
def pageDir = Utils.loadPageDir(node)

def pageFile = new File(pageDir, pageName + '.md')
if (!pageFile.exists()) {
    ui.errorMessage('page file is missing.')
    return
}

java.awt.Desktop.getDesktop().open(pageFile)
UITools.informationMessage('Close this after editing the file finishes.')

Utils.updateNextSteps(pageFile, node)
