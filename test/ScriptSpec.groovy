import spock.lang.Specification
import spock.lang.TempDir

import javax.swing.JOptionPane
import java.nio.file.Path

/**
 * Base class for the specs of the add-on's menu scripts (scripts/*.groovy).
 *
 * The scripts are top-level Groovy statements that Freeplane runs against the
 * bindings it injects (node, c, ui), so they are driven here the same way: a
 * GroovyShell evaluates the real script file with those three bindings faked.
 * lib/ is the main source set, so Utils/SearchIndex resolve from the test
 * classpath exactly as they resolve from the add-on's jar at runtime.
 *
 * The fakes are Expando-based, like UtilsSpec's, and cover only the slice of
 * Freeplane's node/controller/UI API the scripts actually touch. Dialogs are
 * scripted through inputAnswers/confirmAnswer, and everything a script would
 * show or open is recorded (errorMessages, openedInDesktop) for assertions.
 *
 * Scripts that only delegate to Freeplane internals (FoldOneLevel,
 * UnfoldOneLevel, NewMapView, init/init.groovy) import org.freeplane.* classes
 * that aren't available as a dependency, and are therefore out of scope here.
 */
abstract class ScriptSpec extends Specification {

    @TempDir
    Path tempDir

    /** The document directory the fake config node points at. */
    File docDir

    def mindMap
    FakeNode rootNode
    FakeNode configNode

    def ui
    def c

    /** Messages the script passed to ui.errorMessage(). */
    List<String> errorMessages = []
    /** Answers handed out by ui.showInputDialog(), consumed in order; null when exhausted. */
    List<String> inputAnswers = []
    /** Answer returned by ui.showConfirmDialog(). */
    int confirmAnswer = JOptionPane.YES_OPTION
    /** Files the script passed to Utils.openInDesktop() (never opened for real). */
    List<File> openedInDesktop = []
    /** Nodes the script passed to c.select(). */
    List selectedNodes = []
    /** Argument lists of every node.map.filter() call. */
    List<List> filterCalls = []
    /** Directory c.getUserDirectory() reports; the project dir, so scripts/template.md is the real one. */
    File userDirectory = new File(System.getProperty('user.dir'))

    def setup() {
        docDir = tempDir.resolve('docs').toFile()
        docDir.mkdirs()

        mindMap = new Expando()
        mindMap.filter = { Object... args -> filterCalls << args.toList() }
        rootNode = createNode(text: 'root')
        mindMap.root = rootNode
        configNode = addChild(rootNode, text: 'config')
        def docDirPathNode = addChild(configNode, text: 'docDirPath')
        addChild(docDirPathNode, text: docDir.absolutePath)

        ui = new Expando()
        ui.showInputDialog = { Object... args -> inputAnswers.isEmpty() ? null : inputAnswers.remove(0) }
        ui.showConfirmDialog = { Object... args -> confirmAnswer }
        ui.errorMessage = { message -> errorMessages << String.valueOf(message) }

        c = new Expando()
        c.getUserDirectory = { -> userDirectory }
        c.getViewRoot = { -> rootNode }
        c.select = { targetNode -> selectedNodes << targetNode }

        // Replaces the real (headless-hostile, externally side-effecting) desktop call
        // for the duration of the feature method; undone in cleanup().
        Utils.metaClass.static.openInDesktop = { File file -> openedInDesktop << file }
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Utils)
    }

    // --- Fake mind map ---

    FakeNode createNode(Map props = [:]) {
        def node = new FakeNode()
        node.text = props.text ?: ''
        if (props.plainText) node.plainText = props.plainText
        node.link.file = props.linkFile
        node.link.node = props.linkNode
        node.mindMap = mindMap
        return node
    }

    /** Named arguments go first: addChild(parent, text: 'x') passes the map as the first argument. */
    FakeNode addChild(Map props = [:], FakeNode parentNode) {
        return parentNode.insertChild(createNode(props))
    }

    /** Adds a node under the root that links the document directory file for the given name. */
    FakeNode addPageNode(String name, FakeNode parentNode = rootNode) {
        return addChild([text: name, linkFile: new File(docDir, name + '.md')], parentNode)
    }

    FakeNode addDirectoryNode(String name, FakeNode parentNode = rootNode) {
        return addChild([text: name, linkFile: new File(docDir, name)], parentNode)
    }

    // --- Document directory fixtures ---

    File writePage(String name, String text = "# ${name}\n") {
        def pageFile = new File(docDir, name + '.md')
        pageFile.setText(text, 'UTF-8')
        return pageFile
    }

    File makeDir(String name) {
        def dir = new File(docDir, name)
        dir.mkdirs()
        return dir
    }

    /** The BOM character the page-writing scripts prepend, spelled out to stay visible in source. */
    static final String BOM_CHAR = Character.toString((char) 0xFEFF)

    /** Reads a document directory file, dropping the UTF-8 BOM the scripts write. */
    String readPage(String name) {
        def text = new File(docDir, name + '.md').getText('UTF-8')
        return text.startsWith(BOM_CHAR) ? text.substring(1) : text
    }

    boolean hasBom(String name) {
        def bytes = new File(docDir, name + '.md').bytes
        return bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF
    }

    // --- Running the script under test ---

    def runScript(String scriptName, node) {
        def bindings = new Binding([node: node, c: c, ui: ui])
        new GroovyShell(this.class.classLoader, bindings).evaluate(new File('scripts', scriptName))
    }
}
