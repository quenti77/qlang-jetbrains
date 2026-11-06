package dev.qlang.jetbrains

import com.intellij.lang.Language
import com.intellij.lang.Commenter
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object QlangLanguage : Language("qlang")

object QlangIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/qlang.svg", QlangIcons::class.java)
}

class QlangFileType private constructor() : LanguageFileType(QlangLanguage) {
    override fun getName() = "qlang"
    override fun getDescription() = "Fichier qlang"
    override fun getDefaultExtension() = "q"
    override fun getIcon(): Icon = QlangIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = QlangFileType()
    }
}

/** `-- commentaire` et `--( bloc --)`. */
class QlangCommenter : Commenter {
    override fun getLineCommentPrefix() = "-- "
    override fun getBlockCommentPrefix() = "--("
    override fun getBlockCommentSuffix() = "--)"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
