import spock.util.concurrent.PollingConditions

/**
 * Update.groovy is the manual trigger for the two pieces of derived state the
 * startup listener also maintains, and both are asynchronous (Next Steps on a
 * background thread plus the Swing EDT, the search index on its own thread), so
 * these are integration tests over a real temp document directory and a real
 * Lucene index rather than interaction checks.
 */
class UpdateSpec extends ScriptSpec {

    def conditions = new PollingConditions(timeout: 30, initialDelay: 0.1, factor: 1.25)

    def "refreshes the Next Steps children of every page node"() {
        given:
        writePage('Design Memo', """\
# Design Memo

### Next Steps

* draft the outline
* collect references

## Journal
""")
        def node = addPageNode('Design Memo')

        when:
        runScript('Update.groovy', node)

        then:
        conditions.eventually {
            assert node.children.collect { it.text } == ['draft the outline', 'collect references']
        }
    }

    def "builds the full-text search index for the document directory"() {
        given:
        writePage('Design Memo', "# Design Memo\nquantum computing notes\n")
        def node = addPageNode('Design Memo')

        when:
        runScript('Update.groovy', node)

        then:
        conditions.eventually {
            assert new File(docDir, SearchIndex.INDEX_DIR_NAME).isDirectory()
            assert SearchIndex.search(docDir, 'quantum').collect { it.relativePath } == ['Design Memo.md']
        }
    }

    def "reports a missing document directory configuration instead of failing"() {
        given:
        rootNode.removeChild(configNode)
        def node = addChild(rootNode, text: 'Design Memo')

        when:
        runScript('Update.groovy', node)

        then:
        errorMessages == ['config node is missing.']
    }
}
