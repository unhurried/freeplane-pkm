class NewDirectorySpec extends ScriptSpec {

    def "creates the directory, links the node and opens it"() {
        given:
        def node = addChild(rootNode, text: 'Projects')

        when:
        runScript('NewDirectory.groovy', node)

        then:
        errorMessages.isEmpty()
        new File(docDir, 'Projects').isDirectory()
        node.link.file == new File(docDir, 'Projects')
        openedInDesktop == [new File(docDir, 'Projects')]
    }

    def "rejects a directory name containing file system reserved characters"() {
        given:
        def node = addChild(rootNode, text: 'Projects:2026')

        when:
        runScript('NewDirectory.groovy', node)

        then:
        errorMessages == ['directory name includes invalid characters']
        docDir.listFiles().length == 0
        node.link.file == null
    }

    def "refuses to reuse an existing directory"() {
        given:
        makeDir('Projects')
        def node = addChild(rootNode, text: 'Projects')

        when:
        runScript('NewDirectory.groovy', node)

        then:
        errorMessages == ['directory already exists.']
        node.link.file == null
        openedInDesktop.isEmpty()
    }
}
