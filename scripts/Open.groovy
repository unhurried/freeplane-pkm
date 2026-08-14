// Page Nodeならページファイル(.md)を、Directory Nodeならディレクトリを、
// OSの既定アプリケーションで開く。それ以外のNodeでは何もしない。

def docNodeType = Utils.getDocNodeType(node)
if (docNodeType == null) {
    return
}

Utils.openInDesktop(Utils.getLinkedFile(node))
