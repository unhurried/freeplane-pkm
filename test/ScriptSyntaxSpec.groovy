import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Parses every scripts/*.groovy file (including scripts/init/init.groovy) to
 * catch syntax errors before they end up embedded, unparsed, in the packaged
 * add-on (see gradle/packageAddon.gradle, which writes scripts/*.groovy into
 * the .addon.mm XML as plain text - it never compiles them).
 *
 * Stops at Phases.CONVERSION (parsing + AST building), one phase short of
 * name resolution, so scripts can be checked here even though they reference
 * bindings Freeplane injects at runtime (node, c, ui) and, for
 * FoldOneLevel/UnfoldOneLevel/NewMapView/init.groovy, import org.freeplane.*
 * classes that aren't available as a project dependency (see the testing
 * policy in CLAUDE.md) - CONVERSION doesn't try to resolve either.
 */
class ScriptSyntaxSpec extends Specification {

    @Unroll
    def "#scriptFile.name parses without a syntax error"() {
        given:
        def unit = new CompilationUnit()
        unit.addSource(scriptFile)

        when:
        unit.compile(Phases.CONVERSION)

        then:
        noExceptionThrown()

        where:
        scriptFile << allScriptFiles()
    }

    private static List<File> allScriptFiles() {
        def scriptsDir = new File('scripts')
        def topLevel = scriptsDir.listFiles().findAll { it.file && it.name.endsWith('.groovy') }
        def initScript = new File(scriptsDir, 'init/init.groovy')
        return (topLevel + [initScript]).sort { it.name }
    }
}
