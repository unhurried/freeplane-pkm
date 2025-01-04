def exePath = 'C:\\Program Files\\Inazuma Search\\InazumaSearch.exe'
def exeFile = new File(exePath)
java.awt.Desktop.getDesktop().open(exeFile)
