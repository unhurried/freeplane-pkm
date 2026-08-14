import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import java.nio.file.Path

class UtilsSpec extends Specification {

    @TempDir
    Path tempDir

    // --- Helper methods to create mock node structures ---

    def createMockNode(Map props = [:]) {
        def node = new Expando()
        node.text = props.text ?: ''
        node.plainText = props.plainText ?: node.text
        node.children = props.children ?: []
        node.link = new Expando()
        node.link.file = props.linkFile
        node.link.node = props.linkNode
        node.mindMap = props.mindMap
        node.getParent = { -> props.parent }
        node.getChildren = { -> node.children }
        node.createChild = { ->
            def child = createMockNode()
            node.children << child
            return child
        }
        node.delete = { -> }
        return node
    }

    def createMindMapWithConfig(String docDirPath) {
        def docDirNode = createMockNode(plainText: docDirPath)
        def configKeyNode = createMockNode(text: 'docDirPath', children: [docDirNode])
        def configNode = createMockNode(text: 'config', children: [configKeyNode])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        return mindMap
    }

    // --- Tests for loadDocDir ---

    def "loadDocDir returns document directory when config is valid"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(mindMap: mindMap)

        when:
        def result = Utils.loadDocDir(node)

        then:
        result == docDir
    }

    def "loadDocDir supports legacy pageDirPath config key"() {
        given:
        def docDir = tempDir.resolve('pages').toFile()
        docDir.mkdirs()
        def docDirNode = createMockNode(plainText: docDir.absolutePath)
        def configKeyNode = createMockNode(text: 'pageDirPath', children: [docDirNode])
        def configNode = createMockNode(text: 'config', children: [configKeyNode])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        when:
        def result = Utils.loadDocDir(node)

        then:
        result == docDir
    }

    def "loadDocDir throws when config node is missing"() {
        given:
        def rootNode = createMockNode(children: [])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        when:
        Utils.loadDocDir(node)

        then:
        def e = thrown(RuntimeException)
        e.message == 'config node is missing.'
    }

    def "loadDocDir throws when docDirPath node is missing"() {
        given:
        def configNode = createMockNode(text: 'config', children: [])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        when:
        Utils.loadDocDir(node)

        then:
        def e = thrown(RuntimeException)
        e.message == 'docDirPath node is missing.'
    }

    def "loadDocDir throws when document directory does not exist"() {
        given:
        def mindMap = createMindMapWithConfig('/nonexistent/path')
        def node = createMockNode(mindMap: mindMap)

        when:
        Utils.loadDocDir(node)

        then:
        def e = thrown(RuntimeException)
        e.message == 'document directory is missing.'
    }

    // --- Tests for getLinkedFile ---

    def "getLinkedFile returns file from direct link"() {
        given:
        def file = tempDir.resolve('test.md').toFile()
        file.createNewFile()
        def node = createMockNode(linkFile: file)

        when:
        def result = Utils.getLinkedFile(node)

        then:
        result == file
    }

    def "getLinkedFile returns file from linked node"() {
        given:
        def file = tempDir.resolve('test.md').toFile()
        file.createNewFile()
        def linkedNode = createMockNode(linkFile: file)
        def node = createMockNode(linkNode: linkedNode)

        when:
        def result = Utils.getLinkedFile(node)

        then:
        result == file
    }

    def "getLinkedFile returns null when no link exists"() {
        given:
        def node = createMockNode()

        when:
        def result = Utils.getLinkedFile(node)

        then:
        result == null
    }

    // --- Tests for getDocNodeType ---

    def "getDocNodeType returns PAGE when linked to page file"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'TestPage.md')
        pageFile.createNewFile()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'TestPage', linkFile: pageFile, mindMap: mindMap)

        when:
        def result = Utils.getDocNodeType(node)

        then:
        result == Utils.DOC_TARGET_PAGE
    }

    def "getDocNodeType returns DIRECTORY when linked to directory"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def dirFile = new File(docDir, 'TestDir')
        dirFile.mkdirs()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'TestDir', linkFile: dirFile, mindMap: mindMap)

        when:
        def result = Utils.getDocNodeType(node)

        then:
        result == Utils.DOC_TARGET_DIRECTORY
    }

    def "getDocNodeType returns null when no linked file"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'NoLink', mindMap: mindMap)

        when:
        def result = Utils.getDocNodeType(node)

        then:
        result == null
    }

    // --- Tests for getPageFile ---

    def "getPageFile returns file when valid page link"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'MyPage.md')
        pageFile.createNewFile()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'MyPage', linkFile: pageFile, mindMap: mindMap)

        when:
        def result = Utils.getPageFile(node)

        then:
        result == pageFile
    }

    def "getPageFile returns null when no linked file"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'NoLink', mindMap: mindMap)

        when:
        def result = Utils.getPageFile(node)

        then:
        result == null
    }

    def "getPageFile returns null when linked file does not match node text"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def otherFile = new File(docDir, 'Other.md')
        otherFile.createNewFile()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'MyPage', linkFile: otherFile, mindMap: mindMap)

        when:
        def result = Utils.getPageFile(node)

        then:
        result == null
    }

    def "getPageFile throws when page file is missing"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Missing.md')
        // Don't create the file
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def node = createMockNode(text: 'Missing', linkFile: pageFile, mindMap: mindMap)

        when:
        Utils.getPageFile(node)

        then:
        def e = thrown(RuntimeException)
        e.message == 'page file is missing.'
    }

    // --- Tests for updateNextSteps ---

    def "updateNextSteps parses next steps from page file"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
[assets](Task.assets)

## Contents

### Next Steps

* First step
* Second step
* Third step
* Fourth step (should be ignored)

## Journal
"""
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def children = []
        def node = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap)
        node.children = children
        node.getChildren = { -> children }
        node.createChild = { ->
            def child = new Expando()
            child.text = ''
            children << child
            return child
        }

        when:
        Utils.updateNextSteps(node)

        then:
        new PollingConditions(timeout: 2).eventually {
            children.size() == 3
            children[0].text == 'First step'
            children[1].text == 'Second step'
            children[2].text == 'Third step'
        }
    }

    def "updateNextSteps handles dash list items"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
### Next Steps

- Step A
- Step B
"""
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def children = []
        def node = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap)
        node.children = children
        node.getChildren = { -> children }
        node.createChild = { ->
            def child = new Expando()
            child.text = ''
            children << child
            return child
        }

        when:
        Utils.updateNextSteps(node)

        then:
        new PollingConditions(timeout: 2).eventually {
            children.size() == 2
            children[0].text == 'Step A'
            children[1].text == 'Step B'
        }
    }

    def "updateNextSteps stops at next heading"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
### Next Steps

* Only step

### Another Section

* Should not appear
"""
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def children = []
        def node = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap)
        node.children = children
        node.getChildren = { -> children }
        node.createChild = { ->
            def child = new Expando()
            child.text = ''
            children << child
            return child
        }

        when:
        Utils.updateNextSteps(node)

        then:
        new PollingConditions(timeout: 2).eventually {
            children.size() == 1
            children[0].text == 'Only step'
        }
    }

    def "updateNextSteps does nothing when no page file linked"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def children = []
        def node = createMockNode(text: 'NoPage', mindMap: mindMap)
        node.children = children
        node.getChildren = { -> children }

        when:
        Utils.updateNextSteps(node)

        then:
        children.size() == 0
    }

    // --- Tests for collectLinkedFiles ---

    def "collectLinkedFiles returns all directly linked files in the tree"() {
        given:
        def fileA = tempDir.resolve('a.md').toFile()
        def fileB = tempDir.resolve('b.md').toFile()
        fileA.createNewFile()
        fileB.createNewFile()
        def childA = createMockNode(linkFile: fileA)
        def childB = createMockNode(linkFile: fileB)
        def root = createMockNode(children: [childA, childB])

        when:
        def result = Utils.collectLinkedFiles(root)

        then:
        result == [fileA.canonicalFile, fileB.canonicalFile] as Set
    }

    def "collectLinkedFiles collects files from deeply nested children"() {
        given:
        def file = tempDir.resolve('deep.md').toFile()
        file.createNewFile()
        def leaf = createMockNode(linkFile: file)
        def middle = createMockNode(children: [leaf])
        def root = createMockNode(children: [middle])

        when:
        def result = Utils.collectLinkedFiles(root)

        then:
        result.contains(file.canonicalFile)
    }

    def "collectLinkedFiles skips nodes with no link"() {
        given:
        def file = tempDir.resolve('linked.md').toFile()
        file.createNewFile()
        def linkedChild = createMockNode(linkFile: file)
        def unlinkedChild = createMockNode()
        def root = createMockNode(children: [linkedChild, unlinkedChild])

        when:
        def result = Utils.collectLinkedFiles(root)

        then:
        result.size() == 1
        result.contains(file.canonicalFile)
    }

    def "collectLinkedFiles includes files linked via intermediate node"() {
        given:
        def file = tempDir.resolve('via-node.md').toFile()
        file.createNewFile()
        def linkedNode = createMockNode(linkFile: file)
        def child = createMockNode(linkNode: linkedNode)
        def root = createMockNode(children: [child])

        when:
        def result = Utils.collectLinkedFiles(root)

        then:
        result.contains(file.canonicalFile)
    }

    def "collectLinkedFiles returns empty set when no links exist"() {
        given:
        def root = createMockNode(children: [createMockNode(), createMockNode()])

        when:
        def result = Utils.collectLinkedFiles(root)

        then:
        result.isEmpty()
    }

    // --- Tests for collectNodesByLinkedFile ---

    def "collectNodesByLinkedFile maps every linked file in the tree to its node"() {
        given:
        def fileA = tempDir.resolve('a.md').toFile()
        def fileB = tempDir.resolve('b.md').toFile()
        fileA.createNewFile()
        fileB.createNewFile()
        def childA = createMockNode(linkFile: fileA)
        def childB = createMockNode(linkFile: fileB)
        def middle = createMockNode(children: [childB])
        def root = createMockNode(children: [childA, middle])

        when:
        def result = Utils.collectNodesByLinkedFile(root)

        then:
        result[fileA.canonicalFile] == childA
        result[fileB.canonicalFile] == childB
    }

    def "collectNodesByLinkedFile prefers a direct link over an indirect one to the same file"() {
        given:
        def file = tempDir.resolve('shared.md').toFile()
        file.createNewFile()
        def directNode = createMockNode(linkFile: file)
        def linkedNode = createMockNode(linkFile: file)
        def indirectNode = createMockNode(linkNode: linkedNode)
        def root = createMockNode(children: [indirectNode, directNode])

        when:
        def result = Utils.collectNodesByLinkedFile(root)

        then:
        result[file.canonicalFile] == directNode
    }

    // --- Tests for findNodeForFile ---

    def "findNodeForFile resolves a directly linked page file"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Page.md')
        pageFile.createNewFile()
        def pageNode = createMockNode(linkFile: pageFile)
        def nodesByFile = Utils.collectNodesByLinkedFile(createMockNode(children: [pageNode]))

        expect:
        Utils.findNodeForFile(nodesByFile, pageFile, docDir) == pageNode
    }

    def "findNodeForFile resolves a file inside a linked directory to the directory node"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def subDir = new File(docDir, 'Sub')
        subDir.mkdirs()
        def nestedFile = new File(subDir, 'nested.pdf')
        nestedFile.createNewFile()
        def dirNode = createMockNode(linkFile: subDir)
        def nodesByFile = Utils.collectNodesByLinkedFile(createMockNode(children: [dirNode]))

        expect:
        Utils.findNodeForFile(nodesByFile, nestedFile, docDir) == dirNode
    }

    def "findNodeForFile resolves a file under a page's assets directory to the page node"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Page.md')
        pageFile.createNewFile()
        def assetsDir = new File(docDir, 'Page.assets')
        assetsDir.mkdirs()
        def assetFile = new File(assetsDir, 'image.png')
        assetFile.createNewFile()
        def pageNode = createMockNode(linkFile: pageFile)
        def nodesByFile = Utils.collectNodesByLinkedFile(createMockNode(children: [pageNode]))

        expect:
        Utils.findNodeForFile(nodesByFile, assetFile, docDir) == pageNode
    }

    def "findNodeForFile returns null when no node links the file or any ancestor"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def orphanFile = new File(docDir, 'orphan.md')
        orphanFile.createNewFile()
        def nodesByFile = Utils.collectNodesByLinkedFile(createMockNode(children: []))

        expect:
        Utils.findNodeForFile(nodesByFile, orphanFile, docDir) == null
    }

    // --- Tests for nodePathText ---

    def "nodePathText returns the node's own text when it has no parent"() {
        given:
        def rootNode = createMockNode(text: 'root')

        expect:
        Utils.nodePathText(rootNode) == 'root'
    }

    def "nodePathText joins the ancestor chain with ' > '"() {
        given:
        def rootNode = createMockNode(text: 'root')
        def childNode = createMockNode(text: 'child', parent: rootNode)
        def grandchildNode = createMockNode(text: 'grandchild', parent: childNode)

        expect:
        Utils.nodePathText(grandchildNode) == 'root > child > grandchild'
    }

    def "updateNextSteps clears existing children before adding new ones"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
### Next Steps

* New step
"""
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def deleted = []
        def existingChild = new Expando()
        existingChild.text = 'Old step'
        existingChild.delete = { -> deleted << existingChild }
        def children = [existingChild]
        def node = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap)
        node.children = children
        node.getChildren = { -> new ArrayList(children) }
        node.createChild = { ->
            def child = new Expando()
            child.text = ''
            children << child
            return child
        }

        when:
        Utils.updateNextSteps(node)

        then:
        new PollingConditions(timeout: 2).eventually {
            deleted.size() == 1
            deleted[0] == existingChild
        }
    }

    // --- Tests for the sinceMillis gate on updateNextSteps ---

    def "updateNextSteps skips re-reading when file is not modified after sinceMillis"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
### Next Steps

* New step
"""
        pageFile.setLastModified(1000L)
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def existingChild = new Expando()
        existingChild.text = 'Old step'
        def deleted = []
        existingChild.delete = { -> deleted << existingChild }
        def children = [existingChild]
        def node = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap)
        node.children = children
        node.getChildren = { -> new ArrayList(children) }
        node.createChild = { ->
            def child = new Expando()
            child.text = ''
            children << child
            return child
        }

        when:
        Utils.updateNextSteps(node, 2000L)

        then:
        deleted.isEmpty()
        children.size() == 1
        children[0].text == 'Old step'
    }

    def "updateNextSteps re-reads when file is modified after sinceMillis"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
### Next Steps

* New step
"""
        pageFile.setLastModified(3000L)
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def children = []
        def node = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap)
        node.children = children
        node.getChildren = { -> children }
        node.createChild = { ->
            def child = new Expando()
            child.text = ''
            children << child
            return child
        }

        when:
        Utils.updateNextSteps(node, 2000L)

        then:
        new PollingConditions(timeout: 2).eventually {
            children.size() == 1
            children[0].text == 'New step'
        }
    }

    // --- Tests for updateAllNextSteps ---

    def "updateAllNextSteps updates every page node of the map and records the run time"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def taskFile = new File(docDir, 'Task.md')
        taskFile.text = """\
### Next Steps

* First step
"""
        def otherFile = new File(docDir, 'Other.md')
        otherFile.text = """\
### Next Steps

* Other step
"""
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def configNode = mindMap.root.children[0]
        def taskNode = createMockNode(text: 'Task', linkFile: taskFile, mindMap: mindMap)
        def otherNode = createMockNode(text: 'Other', linkFile: otherFile, mindMap: mindMap)
        // The second page node is nested, so the whole tree has to be traversed.
        taskNode.children << otherNode
        mindMap.root.children << taskNode
        mindMap.root.mindMap = mindMap

        when:
        Utils.updateAllNextSteps(mindMap.root)

        then:
        new PollingConditions(timeout: 2).eventually {
            taskNode.children.any { it.text == 'First step' }
            otherNode.children.any { it.text == 'Other step' }
        }
        configNode.children.find { it.text == 'nextStepsUpdatedAt' } != null
    }

    def "updateAllNextSteps skips pages that were not modified since the last run"() {
        given:
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Task.md')
        pageFile.text = """\
### Next Steps

* New step
"""
        pageFile.setLastModified(1000L)
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def configNode = mindMap.root.children[0]
        configNode.children << createMockNode(text: 'nextStepsUpdatedAt',
                children: [createMockNode(plainText: '2000')])
        def deleted = []
        def existingChild = createMockNode(text: 'Old step')
        existingChild.delete = { -> deleted << existingChild }
        def taskNode = createMockNode(text: 'Task', linkFile: pageFile, mindMap: mindMap,
                children: [existingChild])
        mindMap.root.children << taskNode
        mindMap.root.mindMap = mindMap

        when:
        Utils.updateAllNextSteps(mindMap.root)

        then:
        deleted.isEmpty()
        taskNode.children.size() == 1
        taskNode.children[0].text == 'Old step'
    }

    // --- Tests for loadNextStepsUpdatedAt / saveNextStepsUpdatedAt ---

    def "loadNextStepsUpdatedAt returns stored timestamp"() {
        given:
        def valueNode = createMockNode(plainText: '123456789')
        def keyNode = createMockNode(text: 'nextStepsUpdatedAt', children: [valueNode])
        def configNode = createMockNode(text: 'config', children: [keyNode])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        expect:
        Utils.loadNextStepsUpdatedAt(node) == 123456789L
    }

    def "loadNextStepsUpdatedAt returns 0 when timestamp node is missing"() {
        given:
        def configNode = createMockNode(text: 'config', children: [])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        expect:
        Utils.loadNextStepsUpdatedAt(node) == 0L
    }

    def "loadNextStepsUpdatedAt returns 0 when stored value is not a number"() {
        given:
        def valueNode = createMockNode(plainText: 'not-a-number')
        def keyNode = createMockNode(text: 'nextStepsUpdatedAt', children: [valueNode])
        def configNode = createMockNode(text: 'config', children: [keyNode])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        expect:
        Utils.loadNextStepsUpdatedAt(node) == 0L
    }

    def "saveNextStepsUpdatedAt creates the timestamp node when absent"() {
        given:
        def configNode = createMockNode(text: 'config', children: [])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        when:
        Utils.saveNextStepsUpdatedAt(node, 987654321L)

        then:
        def keyNode = configNode.children.find { it.text == 'nextStepsUpdatedAt' }
        keyNode != null
        keyNode.children[0].text == '987654321'
    }

    def "saveNextStepsUpdatedAt updates the existing timestamp node"() {
        given:
        def valueNode = createMockNode(text: '111')
        def keyNode = createMockNode(text: 'nextStepsUpdatedAt', children: [valueNode])
        def configNode = createMockNode(text: 'config', children: [keyNode])
        def rootNode = createMockNode(children: [configNode])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        when:
        Utils.saveNextStepsUpdatedAt(node, 222L)

        then:
        configNode.children.size() == 1
        keyNode.children.size() == 1
        valueNode.text == '222'
    }

    def "saveNextStepsUpdatedAt throws when config node is missing"() {
        given:
        def rootNode = createMockNode(children: [])
        def mindMap = new Expando()
        mindMap.root = rootNode
        def node = createMockNode(mindMap: mindMap)

        when:
        Utils.saveNextStepsUpdatedAt(node, 1L)

        then:
        def e = thrown(RuntimeException)
        e.message == 'config node is missing.'
    }
}
