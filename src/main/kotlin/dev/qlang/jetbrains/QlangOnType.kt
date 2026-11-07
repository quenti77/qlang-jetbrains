package dev.qlang.jetbrains

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit

private val LOG = Logger.getInstance("dev.qlang.jetbrains.OnType")

/** Le temps qu'on laisse au serveur sur Entrée avant de rendre la main (il répond en quelques millisecondes). */
private const val TIMEOUT_MS = 1000

/** Les mots qui se replacent d'eux-mêmes à la frappe de l'espace qui les suit (voir `on_type_formatting` côté serveur). */
private val SPACE_TRIGGERS = setOf("case", "else", "elseif")

/**
 * Entrée : l'IDE n'envoie jamais `textDocument/onTypeFormatting` (ni le client LSP natif, ni LSP4IJ), alors on le fait.
 * Le serveur ajoute le `end` du bloc qu'on vient d'ouvrir (`fun`, `struct`, `impl`, `enum`, `if … then`…) et indente.
 */
class QlangEnterHandler : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if (file.virtualFile?.isQlang() != true) return EnterHandlerDelegate.Result.Continue
        formatOnType(file.project, editor, file, "\n")
        return EnterHandlerDelegate.Result.Continue
    }
}

/** Espace : `case`, `else` et `elseif` se remettent à leur niveau. */
class QlangTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != ' ' || file.virtualFile?.isQlang() != true) return Result.CONTINUE
        val document = editor.document
        val line = document.getLineNumber(editor.caretModel.offset)
        val text = document.getText(com.intellij.openapi.util.TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
        if (text.trim().substringBefore(' ') !in SPACE_TRIGGERS) return Result.CONTINUE
        formatOnType(project, editor, file, " ")
        return Result.CONTINUE
    }
}

private fun formatOnType(project: Project, editor: Editor, file: PsiFile, ch: String) {
    val vfile = file.virtualFile ?: return
    val server = qlangServer(project, vfile) ?: return
    val document = editor.document
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
    val caret = editor.caretModel.offset
    val line = document.getLineNumber(caret)
    val params = DocumentOnTypeFormattingParams(
        TextDocumentIdentifier(server.fileUri(vfile)),
        FormattingOptions(4, true),
        Position(line, caret - document.getLineStartOffset(line)),
        ch,
    )
    val edits = try {
        server.request(TIMEOUT_MS) { it.textDocumentService.onTypeFormatting(params) }
    } catch (e: Exception) {
        LOG.info("onTypeFormatting: ${e.message}")
        null
    }
    if (edits.isNullOrEmpty()) return
    val newCaret = applyEdits(document, edits, caret)
    editor.caretModel.moveToOffset(newCaret)
}

/**
 * Applique les modifications du serveur et rend la nouvelle position du curseur.
 *
 * Les modifications sont données dans l'état du document avant elles toutes. On les applique de la fin vers le
 * début ; à position égale, dans l'ordre reçu : la dernière se place alors avant les autres — c'est ce que le
 * serveur de qlang attend (`\nend` puis l'indentation de la ligne, au même endroit : l'indentation passe devant).
 */
internal fun applyEdits(document: Document, edits: List<TextEdit>, caret: Int): Int {
    fun offset(p: Position): Int {
        val line = p.line.coerceIn(0, document.lineCount - 1)
        if (p.line >= document.lineCount) return document.textLength
        return (document.getLineStartOffset(line) + p.character).coerceAtMost(document.getLineEndOffset(line))
    }
    class E(val start: Int, val end: Int, val text: String)
    val list = edits.map { E(offset(it.range.start), offset(it.range.end), it.newText) }
    // Le curseur : décalé par ce qui se passe avant lui, et placé après une indentation insérée à sa place.
    var newCaret = caret
    for (e in list) {
        when {
            e.end <= caret && !(e.start == e.end && e.start == caret && !e.text.isBlank()) ->
                newCaret += e.text.length - (e.end - e.start)
        }
    }
    val ordered = list.withIndex().sortedWith(compareBy({ -it.value.start }, { it.index })).map { it.value }
    for (e in ordered) document.replaceString(e.start, e.end, e.text)
    // sur une ligne vide, le curseur finit au bout de l'indentation, avant tout `end` ajouté en dessous
    val line = document.getLineNumber(newCaret.coerceIn(0, document.textLength))
    val start = document.getLineStartOffset(line)
    val end = document.getLineEndOffset(line)
    if (document.getText(com.intellij.openapi.util.TextRange(start, end)).isBlank()) return end
    return newCaret.coerceIn(0, document.textLength)
}
