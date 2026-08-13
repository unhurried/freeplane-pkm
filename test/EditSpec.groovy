class EditSpec extends ScriptSpec {

    def "renames the page file, its assets directory and the node"() {
        given:
        writePage('Design Memo', "# Design Memo\n[assets](Design Memo.assets)\n")
        makeDir('Design Memo.assets')
        def node = addPageNode('Design Memo')
        inputAnswers << 'Design Notes'

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages.isEmpty()
        !new File(docDir, 'Design Memo.md').exists()
        !new File(docDir, 'Design Memo.assets').exists()
        new File(docDir, 'Design Notes.md').isFile()
        new File(docDir, 'Design Notes.assets').isDirectory()
        node.text == 'Design Notes'
        node.link.file == new File(docDir, 'Design Notes.md')
    }

    def "rewrites references to the old name inside the page"() {
        given:
        writePage('Design Memo', "# Design Memo\n[assets](Design Memo.assets)\n[see](Design Memo.md)\n")
        makeDir('Design Memo.assets')
        def node = addPageNode('Design Memo')
        inputAnswers << 'Design Notes'

        when:
        runScript('Edit.groovy', node)

        then:
        readPage('Design Notes') == "# Design Memo\n[assets](Design Notes.assets)\n[see](Design Notes.md)\n"
        hasBom('Design Notes')
    }

    // Page names are free text - only file system reserved characters are rejected -
    // so a name may contain regex metacharacters. Rewriting the in-page references
    // with replaceAll() used to either throw ("Memo [1]") or, worse, silently leave
    // the references pointing at the old assets directory ("C++ Memo", where "++"
    // parses as a possessive quantifier instead of two literal plus signs).
    def "rewrites references for names containing regex metacharacters"() {
        given:
        writePage(oldName, "[assets](${oldName}.assets/diagram.png)\n")
        makeDir(oldName + '.assets')
        def node = addPageNode(oldName)
        inputAnswers << newName

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages.isEmpty()
        readPage(newName) == "[assets](${newName}.assets/diagram.png)\n"

        where:
        oldName     | newName
        'C++ Memo'  | 'C++ Notes'
        'Memo [1]'  | 'Memo [2]'
        'A.B Memo'  | 'AXB Memo'
        'Memo (WIP)'| 'Memo (done)'
    }

    def "renames a directory node"() {
        given:
        makeDir('Projects')
        def node = addDirectoryNode('Projects')
        inputAnswers << 'Archive'

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages.isEmpty()
        new File(docDir, 'Archive').isDirectory()
        !new File(docDir, 'Projects').exists()
        node.text == 'Archive'
        node.link.file == new File(docDir, 'Archive')
    }

    def "edits the text of a node that links no document"() {
        given:
        def node = addChild(rootNode, text: 'plain node')
        inputAnswers << 'renamed node'

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages.isEmpty()
        node.text == 'renamed node'
    }

    def "does nothing when the dialog is cancelled"() {
        given:
        writePage('Design Memo')
        def node = addPageNode('Design Memo')
        inputAnswers.clear()

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages.isEmpty()
        new File(docDir, 'Design Memo.md').isFile()
        node.text == 'Design Memo'
    }

    def "rejects a new name containing file system reserved characters"() {
        given:
        writePage('Design Memo')
        def node = addPageNode('Design Memo')
        inputAnswers << 'Design/Notes'

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages == ['new name includes invalid characters']
        new File(docDir, 'Design Memo.md').isFile()
        node.text == 'Design Memo'
    }

    def "refuses to overwrite an existing page"() {
        given:
        writePage('Design Memo')
        writePage('Design Notes')
        def node = addPageNode('Design Memo')
        inputAnswers << 'Design Notes'

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages == ['new page file already exists.']
        new File(docDir, 'Design Memo.md').isFile()
        node.text == 'Design Memo'
    }

    def "refuses to rename onto an existing assets directory"() {
        given:
        writePage('Design Memo')
        makeDir('Design Notes.assets')
        def node = addPageNode('Design Memo')
        inputAnswers << 'Design Notes'

        when:
        runScript('Edit.groovy', node)

        then:
        errorMessages == ['new page assets directory already exists.']
        new File(docDir, 'Design Memo.md').isFile()
        !new File(docDir, 'Design Notes.md').exists()
    }
}
