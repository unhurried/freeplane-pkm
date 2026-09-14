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

    private File nextStepsDocDir() {
        def docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()
        return docDir
    }

    /** A page node for <docDir>/<name>.md holding the given text, attached under the map root. */
    private pageNode(mindMap, File docDir, String name, String pageText, List existingChildren = []) {
        def pageFile = new File(docDir, name + '.md')
        pageFile.text = pageText
        def node = createMockNode(text: name, linkFile: pageFile, mindMap: mindMap, children: existingChildren)
        // A copy, like Freeplane's node model, so children can be deleted while iterating.
        node.getChildren = { -> new ArrayList(node.children) }
        mindMap.root.children << node
        return node
    }

    /**
     * Runs updateAllNextSteps() over a map holding one page ("Task") with the
     * given text and waits until its children read as expected.
     */
    private void expectNextSteps(String pageText, List<String> expected, List existingChildren = []) {
        def docDir = nextStepsDocDir()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        mindMap.root.mindMap = mindMap
        def taskNode = pageNode(mindMap, docDir, 'Task', pageText, existingChildren)

        Utils.updateAllNextSteps(mindMap.root)

        new PollingConditions(timeout: 2).eventually {
            assert taskNode.children*.text == expected
        }
    }

    def "updateAllNextSteps syncs up to three list items under the Next Steps heading"() {
        expect:
        expectNextSteps("""\
[assets](Task.assets)

## Contents

### Next Steps

* First step
* Second step
* Third step
* Fourth step (should be ignored)

## Journal
""", ['First step', 'Second step', 'Third step'])
    }

    def "updateAllNextSteps accepts dash list items"() {
        expect:
        expectNextSteps("""\
### Next Steps

- Step A
- Step B
""", ['Step A', 'Step B'])
    }

    def "updateAllNextSteps stops at the next heading"() {
        expect:
        expectNextSteps("""\
### Next Steps

* Only step

### Another Section

* Should not appear
""", ['Only step'])
    }

    def "updateAllNextSteps replaces the existing children of a changed page"() {
        given:
        def existingChild = createMockNode(text: 'Old step')
        def existingChildren = [existingChild]
        existingChild.delete = { -> existingChildren.remove(existingChild) }

        expect:
        expectNextSteps("""\
### Next Steps

* New step
""", ['New step'], existingChildren)
    }

    def "updateAllNextSteps updates every page node of the map"() {
        given:
        def docDir = nextStepsDocDir()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        mindMap.root.mindMap = mindMap
        def taskNode = pageNode(mindMap, docDir, 'Task', "### Next Steps\n\n* First step\n")
        // Nested under the first page, so the whole tree has to be traversed.
        def otherFile = new File(docDir, 'Other.md')
        otherFile.text = "### Next Steps\n\n* Other step\n"
        def otherNode = createMockNode(text: 'Other', linkFile: otherFile, mindMap: mindMap)
        taskNode.children << otherNode

        when:
        Utils.updateAllNextSteps(mindMap.root)

        then:
        new PollingConditions(timeout: 2).eventually {
            assert taskNode.children.any { it.text == 'First step' }
            assert otherNode.children*.text == ['Other step']
        }
    }

    def "updateAllNextSteps leaves a page alone whose children already match"() {
        given:
        def docDir = nextStepsDocDir()
        def mindMap = createMindMapWithConfig(docDir.absolutePath)
        mindMap.root.mindMap = mindMap
        def deleted = []
        def existingChild = createMockNode(text: 'Same step')
        existingChild.delete = { -> deleted << existingChild }
        def unchangedNode = pageNode(mindMap, docDir, 'Unchanged', "### Next Steps\n\n* Same step\n", [existingChild])
        // Applied in the same EDT batch as the unchanged page, so once it shows up
        // the unchanged page has been evaluated too.
        def changedNode = pageNode(mindMap, docDir, 'Changed', "### Next Steps\n\n* New step\n")

        when:
        Utils.updateAllNextSteps(mindMap.root)

        then:
        new PollingConditions(timeout: 2).eventually {
            assert changedNode.children*.text == ['New step']
        }
        deleted.isEmpty()
        unchangedNode.children == [existingChild]
    }
}
