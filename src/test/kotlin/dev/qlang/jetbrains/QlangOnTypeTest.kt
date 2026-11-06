package dev.qlang.jetbrains

import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateService
import java.nio.file.Files
import java.nio.file.Path

/**
 * Essais avec le vrai serveur : `QLANG_BIN=/chemin/vers/qlang ./gradlew test`.
 * Sans QLANG_BIN, les essais qui lancent le serveur sont sautés.
 */
class QlangOnTypeTest : BasePlatformTestCase() {
    private val bin: String? = System.getenv("QLANG_BIN")?.takeIf { it.isNotBlank() && Files.isExecutable(Path.of(it)) }

    override fun setUp() {
        super.setUp()
        if (bin != null) QlangSettings.getInstance().executablePath = bin
    }

    override fun tearDown() {
        try {
            QlangSettings.getInstance().executablePath = ""
        } finally {
            super.tearDown()
        }
    }

    fun testFileTypeAndIcon() {
        val f = myFixture.configureByText("a.q", "")
        assertEquals("qlang", f.virtualFile.fileType.name)
        assertNotNull(QlangFileType.INSTANCE.icon)
    }

    fun testTextMateGrammarIsRegistered() {
        val d = TextMateService.getInstance().getLanguageDescriptorByFileName("a.q")
        assertNotNull("la grammaire source.qlang n'est pas enregistrée", d)
    }

    fun testExecutableResolution() {
        assertNull(QlangExecutable.resolve("/nope/qlang"))
        if (bin != null) assertEquals(Path.of(bin), QlangExecutable.resolve(bin))
    }

    private fun assertResult(expected: String) {
        val doc = myFixture.editor.document.text
        val caret = myFixture.editor.caretModel.offset
        assertEquals(expected, doc.substring(0, caret) + "<caret>" + doc.substring(caret))
    }

    private fun waitForServer() {
        myFixture.file // ouvre l'éditeur
        PlatformTestUtil.waitWithEventsDispatching("le serveur qlang n'a pas démarré", {
            LspServerManager.getInstance(project).getServersForProvider(QlangLspServerSupportProvider::class.java)
                .any { it.state == com.intellij.platform.lsp.api.LspServerState.Running }
        }, 20)
        // le client envoie didOpen un peu après le démarrage : on le laisse arriver (dans l'IDE, le fichier est ouvert bien avant la frappe)
        repeat(20) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(50)
        }
    }

    private fun enter(before: String, expected: String) {
        if (bin == null) return
        myFixture.configureByText("t.q", before)
        waitForServer()
        myFixture.type("\n")
        assertResult(expected)
    }

    fun testEnterAfterFun() = enter("fun salut()<caret>", "fun salut()\n    <caret>\nend")
    fun testEnterAfterStruct() = enter("struct Point<caret>", "struct Point\n    <caret>\nend")
    fun testEnterAfterImpl() = enter("impl Point<caret>", "impl Point\n    <caret>\nend")
    fun testEnterAfterEnum() = enter("enum E<caret>", "enum E\n    <caret>\nend")
    fun testEnterAfterIf() = enter("if x then<caret>", "if x then\n    <caret>\nend")
    fun testEnterInsideBody() = enter("fun f()\n    let a = 1<caret>\nend", "fun f()\n    let a = 1\n    <caret>\nend")
    fun testEnterWithBlockBelow() = enter("fun f()<caret>\nfun g()\nend", "fun f()\n    <caret>\nend\nfun g()\nend")

    fun testSpaceReindentsElse() {
        if (bin == null) return
        myFixture.configureByText("t.q", "fun f()\n    if x then\n        1\n        else<caret>\n    end\nend")
        waitForServer()
        myFixture.type(" ")
        assertResult("fun f()\n    if x then\n        1\n    else <caret>\n    end\nend")
    }

    fun testSpaceReindentsCase() {
        if (bin == null) return
        myFixture.configureByText("t.q", "match x\ncase<caret>\nend")
        waitForServer()
        myFixture.type(" ")
        assertResult("match x\n    case <caret>\nend")
    }

    fun testReformatCodeUsesQlangFmt() {
        if (bin == null) return
        myFixture.configureByText("t.q", "fun f()\n        let a   =  1\nend\n")
        waitForServer()
        myFixture.performEditorAction("ReformatCode")
        assertEquals("fun f()\n    let a = 1\nend\n", myFixture.editor.document.text)
    }

    fun testHighlighting() {
        val f = myFixture.configureByText("t.q", "fun f()\n    let a = 1 -- note\nend\n")
        val hl = com.intellij.openapi.editor.highlighter.EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, f.virtualFile)
        hl.setText(myFixture.editor.document.text)
        val it = hl.createIterator(0)
        val seen = mutableSetOf<String>()
        while (!it.atEnd()) { seen += it.tokenType.toString(); it.advance() }
        println("TOKENS: " + seen + " HL=" + hl.javaClass.name)
        assertTrue("pas de jetons TextMate : $seen", seen.any { "qlang" in it })
    }
}
