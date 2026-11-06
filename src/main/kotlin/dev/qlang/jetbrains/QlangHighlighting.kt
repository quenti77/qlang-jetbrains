package dev.qlang.jetbrains

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateEditorHighlighterProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Enregistre la grammaire TextMate de qlang (`source.qlang`) auprès du plugin TextMate de la plateforme.
 * Le plugin TextMate lit un dossier, pas une ressource : on y copie le paquet embarqué.
 */
class QlangTextMateBundleProvider : TextMateBundleProvider {
    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val dir = extract() ?: return emptyList()
        return listOf(TextMateBundleProvider.PluginBundle("qlang", dir))
    }

    companion object {
        private val FILES = listOf(
            "package.json",
            "language-configuration.json",
            "syntaxes/qlang.tmLanguage.json",
        )

        @Volatile
        private var cached: Path? = null

        fun extract(): Path? {
            cached?.let { if (Files.isDirectory(it)) return it }
            return try {
                val dir = Path.of(PathManager.getSystemPath(), "qlang-jetbrains", "textmate", "qlang")
                for (f in FILES) {
                    val target = dir.resolve(f)
                    Files.createDirectories(target.parent)
                    val src = QlangTextMateBundleProvider::class.java.getResourceAsStream("/textmate/qlang/$f")
                        ?: error("ressource manquante : $f")
                    src.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                }
                dir.also { cached = it }
            } catch (e: Exception) {
                com.intellij.openapi.diagnostic.Logger.getInstance(QlangTextMateBundleProvider::class.java)
                    .warn("grammaire TextMate de qlang non installée", e)
                null
            }
        }
    }
}

/**
 * La coloration des `*.q` vient de la grammaire TextMate, mais le type de fichier est le nôtre (icône, serveur) :
 * on prête donc à notre type de fichier le colorateur d'éditeur du plugin TextMate, qui sait enregistrer les
 * types de jetons de la grammaire.
 */
class QlangEditorHighlighterProvider : EditorHighlighterProvider {
    private val delegate = TextMateEditorHighlighterProvider()
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): EditorHighlighter = delegate.getEditorHighlighter(project, fileType, virtualFile, colors)
}
