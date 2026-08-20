import java.time.LocalDate

/**
 * Filter.groovy hands Freeplane a predicate through node.map.filter(); the fake
 * map records those calls (see ScriptSpec), so each command is checked by
 * evaluating the recorded predicate against fake nodes.
 */
class FilterSpec extends ScriptSpec {

    Closure lastPredicate() {
        return filterCalls.last().last() as Closure
    }

    List lastFlags() {
        def args = filterCalls.last()
        return args.size() > 1 ? args[0..-2] : []
    }

    def "an empty command hides everything under an archive node"() {
        given:
        def archive = addChild(rootNode, text: 'Archive')
        def archived = addChild(addChild(archive, text: 'old project'), text: 'note')
        def visible = addChild(rootNode, text: 'current project')

        when:
        runScript('Filter.groovy', rootNode)

        then:
        lastFlags() == []
        !lastPredicate().call(archive)
        !lastPredicate().call(archived)
        lastPredicate().call(visible)
    }

    def "the 'a' command shows every node"() {
        given:
        def archived = addChild(addChild(rootNode, text: 'archive'), text: 'note')
        inputAnswers << 'a'

        when:
        runScript('Filter.groovy', rootNode)

        then:
        lastFlags() == [true, true]
        lastPredicate().call(archived)
    }

    def "the 't' command shows only the ToDo node at the top level"() {
        given:
        def toDo = addChild(rootNode, text: 'ToDo')
        def toDoItem = addChild(toDo, text: 'write the draft')
        def nestedToDo = addChild(addChild(rootNode, text: 'project'), text: 'ToDo')
        inputAnswers << 't'

        when:
        runScript('Filter.groovy', rootNode)

        then:
        lastPredicate().call(toDo)
        !lastPredicate().call(toDoItem)
        !lastPredicate().call(nestedToDo)
        !lastPredicate().call(rootNode)
    }

    def "the 'd' command shows items due today or overdue"() {
        given:
        def overdue = addChild(rootNode, text: LocalDate.now().minusDays(1).format('yy/MM/dd') + ' pay the bill')
        def dueToday = addChild(rootNode, text: LocalDate.now().format('yy/MM/dd') + ' send the report')
        def upcoming = addChild(rootNode, text: LocalDate.now().plusDays(1).format('yy/MM/dd') + ' review')
        def undated = addChild(rootNode, text: 'someday')
        inputAnswers << 'd'

        when:
        runScript('Filter.groovy', rootNode)

        then:
        lastPredicate().call(overdue)
        lastPredicate().call(dueToday)
        !lastPredicate().call(upcoming)
        !lastPredicate().call(undated)
    }

    def "any other command filters by case-insensitive substring"() {
        given:
        def matching = addChild(rootNode, text: 'Design MEMO')
        def other = addChild(rootNode, text: 'meeting notes')
        inputAnswers << 'memo'

        when:
        runScript('Filter.groovy', rootNode)

        then:
        lastPredicate().call(matching)
        !lastPredicate().call(other)
    }

    def "an uppercase keyword still matches a lowercase node text"() {
        given:
        def matching = addChild(rootNode, text: 'WSL上のUbuntuを22から24にアップデートする')
        def other = addChild(rootNode, text: 'meeting notes')
        inputAnswers << 'WSL'

        when:
        runScript('Filter.groovy', rootNode)

        then:
        lastPredicate().call(matching)
        !lastPredicate().call(other)
    }
}
