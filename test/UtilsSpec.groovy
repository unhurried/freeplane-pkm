import spock.util.concurrent.PollingConditions

/**
 * Utils is exercised against the same fake map ScriptSpec builds for the
 * scripts (root > config > docDirPath > [docDir], FakeNode nodes), since the
 * two must agree on what a page or directory node looks like.
 */
class UtilsSpec extends ScriptSpec {

    FakeNode toDoNode

    def setup() {
        toDoNode = addChild(rootNode, text: Utils.TODO_NODE_NAME)
    }

    // --- Tests for loadDocDir ---

    def "loadDocDir returns document directory when config is valid"() {
        expect:
        Utils.loadDocDir(rootNode) == docDir
    }

    def "loadDocDir throws when config node is missing"() {
        given:
        rootNode.removeChild(configNode)

        when:
        Utils.loadDocDir(rootNode)

        then:
        def e = thrown(RuntimeException)
        e.message == 'config node is missing.'
    }

    def "loadDocDir throws when docDirPath node is missing"() {
        given:
        Utils.deleteChildren(configNode)

        when:
        Utils.loadDocDir(rootNode)

        then:
        def e = thrown(RuntimeException)
        e.message == 'docDirPath node is missing.'
    }

    def "loadDocDir throws when document directory does not exist"() {
        given:
        configNode.children[0].children[0].text = '/nonexistent/path'

        when:
        Utils.loadDocDir(rootNode)

        then:
        def e = thrown(RuntimeException)
        e.message == 'document directory is missing.'
    }

    // --- Tests for getLinkedFile ---

    def "getLinkedFile returns file from direct link"() {
        given:
        def file = writePage('test')

        expect:
        Utils.getLinkedFile(createNode(linkFile: file)) == file
    }

    def "getLinkedFile returns file from linked node"() {
        given:
        def file = writePage('test')
        def linkedNode = createNode(linkFile: file)

        expect:
        Utils.getLinkedFile(createNode(linkNode: linkedNode)) == file
    }

    def "getLinkedFile returns null when no link exists"() {
        expect:
        Utils.getLinkedFile(createNode()) == null
    }

    // --- Tests for getDocNodeType ---

    def "getDocNodeType returns PAGE when linked to page file"() {
        given:
        writePage('TestPage')

        expect:
        Utils.getDocNodeType(addPageNode('TestPage')) == Utils.DOC_TARGET_PAGE
    }

    def "getDocNodeType returns DIRECTORY when linked to directory"() {
        given:
        makeDir('TestDir')

        expect:
        Utils.getDocNodeType(addDirectoryNode('TestDir')) == Utils.DOC_TARGET_DIRECTORY
    }

    def "getDocNodeType returns null when no linked file"() {
        expect:
        Utils.getDocNodeType(addChild(rootNode, text: 'NoLink')) == null
    }

    def "getDocNodeType accepts an already loaded document directory without consulting the config"() {
        given:
        writePage('TestPage')
        def pageNode = addPageNode('TestPage')
        rootNode.removeChild(configNode)

        expect:
        Utils.getDocNodeType(pageNode, docDir) == Utils.DOC_TARGET_PAGE
    }

    // --- Tests for the name <-> file conventions ---

    def "pageNameOf strips the page suffix and rejects other files"() {
        expect:
        Utils.pageNameOf(new File(docDir, 'Page.md')) == 'Page'
        Utils.pageNameOf(new File(docDir, 'Page.assets')) == null
        Utils.pageNameOf(new File(docDir, 'notes.txt')) == null
    }

    def "pageFileOfAssetsDir maps an assets directory to its page and rejects other directories"() {
        expect:
        Utils.pageFileOfAssetsDir(new File(docDir, 'Page.assets')) == new File(docDir, 'Page.md')
        Utils.pageFileOfAssetsDir(new File(docDir, 'Page')) == null
    }

    def "isPagePath accepts only page files outside assets directories"() {
        expect:
        Utils.isPagePath(relPath) == expected

        where:
        relPath                | expected
        'Page.md'              | true
        'Dir/Page.md'          | true
        'assets.md'            | true
        'Page.assets/Note.md'  | false
        'Page.assets/sub/x.md' | false
        'Page.pdf'             | false
    }

    // --- Tests for collectNodesByLinkedFile ---

    def "collectNodesByLinkedFile maps every linked file in the tree to its node"() {
        given:
        def fileA = writePage('a')
        def fileB = writePage('b')
        def childA = addPageNode('a')
        def childB = addPageNode('b', addChild(rootNode, text: 'middle'))

        when:
        def result = Utils.collectNodesByLinkedFile(rootNode)

        then:
        result.keySet() == [fileA, fileB] as Set
        result[fileA] == childA
        result[fileB] == childB
    }

    def "collectNodesByLinkedFile includes files linked via an intermediate node"() {
        given:
        def file = writePage('via-node')
        def linkedNode = createNode(linkFile: file)
        def child = addChild(rootNode, linkNode: linkedNode)

        expect:
        Utils.collectNodesByLinkedFile(rootNode)[file] == child
    }

    def "collectNodesByLinkedFile prefers a direct link over an indirect one to the same file"() {
        given:
        def file = writePage('shared')
        def linkedNode = createNode(linkFile: file)
        addChild(rootNode, linkNode: linkedNode)
        def directNode = addPageNode('shared')

        expect:
        Utils.collectNodesByLinkedFile(rootNode)[file] == directNode
    }

    def "collectNodesByLinkedFile keys by normalized path, so a link with a redundant segment still matches"() {
        given:
        def file = writePage('a')
        def child = addChild(rootNode, linkFile: new File(docDir, './a.md'))

        expect:
        Utils.collectNodesByLinkedFile(rootNode)[file] == child
    }

    // --- Tests for findNodeForFile ---

    def "findNodeForFile resolves a directly linked page file"() {
        given:
        def pageFile = writePage('Page')
        def pageNode = addPageNode('Page')
        def nodesByFile = Utils.collectNodesByLinkedFile(rootNode)

        expect:
        Utils.findNodeForFile(nodesByFile, pageFile, docDir) == pageNode
    }

    def "findNodeForFile resolves a file inside a linked directory to the directory node"() {
        given:
        def subDir = makeDir('Sub')
        def nestedFile = new File(subDir, 'nested.pdf')
        nestedFile.createNewFile()
        def dirNode = addDirectoryNode('Sub')
        def nodesByFile = Utils.collectNodesByLinkedFile(rootNode)

        expect:
        Utils.findNodeForFile(nodesByFile, nestedFile, docDir) == dirNode
    }

    def "findNodeForFile resolves a file under a page's assets directory to the page node"() {
        given:
        writePage('Page')
        def assetFile = new File(makeDir('Page.assets'), 'image.png')
        assetFile.createNewFile()
        def pageNode = addPageNode('Page')
        def nodesByFile = Utils.collectNodesByLinkedFile(rootNode)

        expect:
        Utils.findNodeForFile(nodesByFile, assetFile, docDir) == pageNode
    }

    def "findNodeForFile returns null when no node links the file or any ancestor"() {
        given:
        def orphanFile = writePage('orphan')
        def nodesByFile = Utils.collectNodesByLinkedFile(rootNode)

        expect:
        Utils.findNodeForFile(nodesByFile, orphanFile, docDir) == null
    }

    def "findNodeForFile resolves a hit reached through a non-canonical path"() {
        given: 'the node links the page directly, while the hit names the same file via a redundant "." segment'
        def pageFile = writePage('Page')
        def pageNode = addPageNode('Page')
        def nodesByFile = Utils.collectNodesByLinkedFile(rootNode)
        def sameFileOtherPath = new File(docDir.path + '/./Page.md')

        expect: 'both spellings still normalize to the same file'
        sameFileOtherPath.path != pageFile.path
        Utils.findNodeForFile(nodesByFile, sameFileOtherPath, docDir) == pageNode
        Utils.findNodeForFile(nodesByFile, pageFile, docDir) == pageNode
    }

    // --- Tests for nodePathText ---

    def "nodePathText returns the node's own text when it has no parent"() {
        expect:
        Utils.nodePathText(rootNode) == 'root'
    }

    def "nodePathText joins the ancestor chain with ' > '"() {
        given:
        def grandchildNode = addChild(addChild(rootNode, text: 'child'), text: 'grandchild')

        expect:
        Utils.nodePathText(grandchildNode) == 'root > child > grandchild'
    }

    // --- Tests for updateAllNextSteps ---

    /** A page node for <docDir>/<name>.md holding the given text, attached under the map root. */
    private FakeNode pageNode(String name, String pageText, List<String> existingChildren = []) {
        writePage(name, pageText)
        def node = addPageNode(name)
        existingChildren.each { addChild(node, text: it) }
        return node
    }

    /**
     * Runs updateAllNextSteps() over a map holding one page ("Task") with the
     * given text and waits until its children read as expected.
     */
    private void expectNextSteps(String pageText, List<String> expected, List<String> existingChildren = []) {
        def taskNode = pageNode('Task', pageText, existingChildren)

        Utils.updateAllNextSteps(rootNode)

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
        expect:
        expectNextSteps("""\
### Next Steps

* New step
""", ['New step'], ['Old step'])
    }

    def "updateAllNextSteps updates every page node of the map"() {
        given:
        def taskNode = pageNode('Task', "### Next Steps\n\n* First step\n")
        // Nested below a plain node, so the whole tree has to be traversed.
        writePage('Other', "### Next Steps\n\n* Other step\n")
        def otherNode = addPageNode('Other', addChild(rootNode, text: 'folder'))

        when:
        Utils.updateAllNextSteps(rootNode)

        then:
        new PollingConditions(timeout: 2).eventually {
            assert taskNode.children.any { it.text == 'First step' }
            assert otherNode.children*.text == ['Other step']
        }
    }

    def "updateAllNextSteps leaves a page alone whose children already match"() {
        given:
        def unchangedNode = pageNode('Unchanged', "### Next Steps\n\n* Same step\n", ['Same step'])
        def existingChild = unchangedNode.children[0]
        // Applied in the same EDT batch as the unchanged page, so once it shows up
        // the unchanged page has been evaluated too.
        def changedNode = pageNode('Changed', "### Next Steps\n\n* New step\n")

        when:
        Utils.updateAllNextSteps(rootNode)

        then:
        new PollingConditions(timeout: 2).eventually {
            assert changedNode.children*.text == ['New step']
        }
        !existingChild.deleted
        unchangedNode.children == [existingChild]
    }

    // Freeplane doesn't filter nodes a script creates in the active map view, so
    // new children of a page the filter hides would show up hanging off the root.
    // The reapply lands in the same EDT batch as the children, so it has happened
    // once expectNextSteps sees them.
    def "updateAllNextSteps reapplies the map's filter once after creating Next Steps children"() {
        given:
        pageNode('Other', "### Next Steps\n\n* Other step\n")

        expect:
        expectNextSteps("### Next Steps\n\n* First step\n", ['First step'])
        filterReapplications == [mindMap]
    }

    def "updateAllNextSteps leaves the filter alone when it creates no node"() {
        given:
        def unchangedNode = pageNode('Unchanged', "### Next Steps\n\n* Same step\n", ['Same step'])

        expect:
        expectNextSteps("### Next Steps\n", [], ['Gone step'])
        unchangedNode.children*.text == ['Same step']
        filterReapplications.isEmpty()
    }

    // --- Tests for the ToDo cleanup of updateAllNextSteps ---

    /** A ToDo item the way AddToToDo creates it: "item (page)" linking <docDir>/<page>.md. */
    private FakeNode toDoItem(String item, String page, FakeNode parentNode = toDoNode) {
        return addChild(parentNode, text: Utils.toDoItemText(item, page), linkFile: Utils.pageFile(docDir, page))
    }

    /**
     * Runs updateAllNextSteps() and waits for the item to be deleted. Deletions
     * land in one EDT batch, so the other ToDo children have been judged by then.
     */
    private void expectDeleted(FakeNode item) {
        Utils.updateAllNextSteps(rootNode)

        new PollingConditions(timeout: 2).eventually {
            assert item.deleted
        }
    }

    def "updateAllNextSteps deletes a ToDo item that is gone from its page's Next Steps"() {
        given:
        pageNode('Task', "### Next Steps\n\n* Kept step\n")
        def gone = toDoItem('Gone step', 'Task')
        // Checking an item off (ToggleCheckmark) doesn't spare it.
        gone.icons.addIcon('button_ok')
        def kept = toDoItem('Kept step', 'Task')

        expect:
        expectDeleted(gone)
        !kept.deleted
        toDoNode.children == [kept]
    }

    def "updateAllNextSteps keeps a ToDo item listed beyond the three synced into the page node"() {
        given:
        def fourth = toDoItem('Four', 'Task')

        expect:
        expectNextSteps("### Next Steps\n\n* One\n* Two\n* Three\n* Four\n", ['One', 'Two', 'Three'])
        !fourth.deleted
        toDoNode.children == [fourth]
    }

    def "updateAllNextSteps leaves ToDo children alone that don't link a page"() {
        given:
        pageNode('Task', "### Next Steps\n\n* Other step\n")
        def unlinked = addChild(toDoNode, text: 'call the plumber')
        def external = addChild(toDoNode, text: 'read it (notes)', linkFile: new File(tempDir.toFile(), 'notes.md'))
        def stale = toDoItem('Gone step', 'Task')

        expect:
        expectDeleted(stale)
        toDoNode.children == [unlinked, external]
    }

    def "updateAllNextSteps deletes a ToDo item whose page file is missing"() {
        given:
        def orphan = toDoItem('Some step', 'Removed Page')

        expect:
        expectDeleted(orphan)
        toDoNode.children.isEmpty()
    }

    def "updateAllNextSteps only judges the direct children of the ToDo node"() {
        given:
        pageNode('Task', "### Next Steps\n\n* Kept step\n")
        def kept = toDoItem('Kept step', 'Task')
        // A sub-note that happens to look like a stale item: stays with its parent.
        def note = toDoItem('Gone step', 'Task', kept)
        def archived = toDoItem('Gone step', 'Task', addChild(toDoNode, text: 'archive'))
        def stale = toDoItem('Gone step', 'Task')

        expect:
        expectDeleted(stale)
        !note.deleted
        !archived.deleted
        kept.children == [note]
    }
}
