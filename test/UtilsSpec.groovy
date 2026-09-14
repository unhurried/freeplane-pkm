import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import javax.swing.SwingUtilities
import java.nio.file.Path

class UtilsSpec extends Specification {

    @TempDir
    Path tempDir

    def cleanup() {
        // updateAllNextSteps() releases its in-progress guard at the very end of
        // the EDT job that applies its results, i.e. possibly a moment after a
        // test has already observed those results. Draining the EDT here keeps
        // the next test's call from being swallowed as an "overlapping" run.
        SwingUtilities.invokeAndWait { }
    }

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
        result.keySet() == [fileA, fileB] as Set
        result[fileA] == childA
        result[fileB] == childB
    }

    def "collectNodesByLinkedFile includes files linked via an intermediate node"() {
        given:
        def file = tempDir.resolve('via-node.md').toFile()
        file.createNewFile()
        def linkedNode = createMockNode(linkFile: file)
        def child = createMockNode(linkNode: linkedNode)
        def root = createMockNode(children: [child])

        when:
        def result = Utils.collectNodesByLinkedFile(root)

        then:
        result[file] == child
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
        result[file] == directNode
    }

    def "collectNodesByLinkedFile keys by normalized path, so a link with a redundant segment still matches"() {
        given:
        def file = tempDir.resolve('a.md').toFile()
        file.createNewFile()
        def child = createMockNode(linkFile: new File(tempDir.toFile(), './a.md'))
        def root = createMockNode(children: [child])

        expect:
        Utils.collectNodesByLinkedFile(root)[file] == child
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

    def "findNodeForFile resolves a hit reached through a non-canonical path"() {
        given: 'the node links the page directly, while the hit names the same file via a redundant "." segment'
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        def pageFile = new File(docDir, 'Page.md')
        pageFile.createNewFile()
        def pageNode = createMockNode(linkFile: pageFile)
        def nodesByFile = Utils.collectNodesByLinkedFile(createMockNode(children: [pageNode]))
        def sameFileOtherPath = new File(docDir.path + '/./Page.md')

        expect: 'both spellings still normalize to the same file'
        sameFileOtherPath.path != pageFile.path
        Utils.findNodeForFile(nodesByFile, sameFileOtherPath, docDir) == pageNode
        Utils.findNodeForFile(nodesByFile, pageFile, docDir) == pageNode
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

    // --- Tests for updateAllNextSteps ---

    /**
     * Runs updateAllNextSteps() over a map holding one page node ("Task")
     * whose file has the given text, waits for the children to be applied on
     * the EDT, and returns them. existingChildren are attached to the page
     * node beforehand.
     */
    private List nextStepsOf(String pageText, List existingChildren = []) {
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        new File(docDir, 'Task.md').text = pageText
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        def taskNode = createMockNode(text: 'Task', linkFile: new File(docDir, 'Task.md'), mindMap: mindMap,
                children: existingChildren)
        // A copy, like Freeplane's node model, so children can be deleted while iterating.
        taskNode.getChildren = { -> new ArrayList(taskNode.children) }
        mindMap.root.children << taskNode
        mindMap.root.mindMap = mindMap

        Utils.updateAllNextSteps(mindMap.root)

        new PollingConditions(timeout: 2).eventually {
            mindMap.root.children.find { it.text == 'config' }.children.any { it.text == 'nextStepsUpdatedAt' }
        }
        return taskNode.children
    }

    def "updateAllNextSteps syncs up to three list items under the Next Steps heading"() {
        expect:
        nextStepsOf("""\
[assets](Task.assets)

## Contents

### Next Steps

* First step
* Second step
* Third step
* Fourth step (should be ignored)

## Journal
""")*.text == ['First step', 'Second step', 'Third step']
    }

    def "updateAllNextSteps accepts dash list items"() {
        expect:
        nextStepsOf("""\
### Next Steps

- Step A
- Step B
""")*.text == ['Step A', 'Step B']
    }

    def "updateAllNextSteps stops at the next heading"() {
        expect:
        nextStepsOf("""\
### Next Steps

* Only step

### Another Section

* Should not appear
""")*.text == ['Only step']
    }

    def "updateAllNextSteps replaces the existing children of a changed page"() {
        given:
        def existingChild = createMockNode(text: 'Old step')
        def existingChildren = [existingChild]
        existingChild.delete = { -> existingChildren.remove(existingChild) }

        expect:
        nextStepsOf("""\
### Next Steps

* New step
""", existingChildren)*.text == ['New step']
    }

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
