class ToggleCheckmarkSpec extends ScriptSpec {

    def "adds the checkmark icon to a node that has none"() {
        given:
        def node = addChild(rootNode, text: 'task')

        when:
        runScript('ToggleCheckmark.groovy', node)

        then:
        node.icons.icons == ['button_ok']
    }

    def "removes the checkmark icon from a node that has it"() {
        given:
        def node = addChild(rootNode, text: 'task')
        node.icons.addIcon('button_ok')

        when:
        runScript('ToggleCheckmark.groovy', node)

        then:
        node.icons.icons.isEmpty()
    }

    def "leaves other icons untouched"() {
        given:
        def node = addChild(rootNode, text: 'task')
        node.icons.addIcon('bookmark')

        when:
        runScript('ToggleCheckmark.groovy', node)

        then:
        node.icons.icons == ['bookmark', 'button_ok']
    }
}
