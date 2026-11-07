package dev.qlang.jetbrains

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.APP)
@State(name = "QlangSettings", storages = [Storage("qlang.xml")])
class QlangSettings : PersistentStateComponent<QlangSettings.State> {
    class State {
        /** Chemin de l'exécutable `qlang` ; vide = le chercher dans le PATH. */
        var executablePath: String = ""
    }

    private var state = State()
    override fun getState() = state
    override fun loadState(state: State) {
        this.state = state
    }

    var executablePath: String
        get() = state.executablePath
        set(value) {
            state.executablePath = value.trim()
        }

    companion object {
        fun getInstance(): QlangSettings = ApplicationManager.getApplication().getService(QlangSettings::class.java)
    }
}

object QlangExecutable {
    /**
     * L'exécutable à lancer : le chemin donné dans les réglages, sinon `qlang` dans le PATH, sinon les dossiers
     * habituels (un IDE lancé depuis le bureau n'a pas toujours le PATH du terminal). `null` s'il est introuvable.
     */
    fun resolve(configured: String = QlangSettings.getInstance().executablePath): Path? {
        if (configured.isNotBlank()) {
            val p = Path.of(configured.trim())
            return p.takeIf { Files.isRegularFile(it) && Files.isExecutable(it) }
        }
        val home = System.getProperty("user.home")
        val name = if (System.getProperty("os.name").startsWith("Windows")) "qlang.exe" else "qlang"
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }
        return (pathDirs + listOf("$home/.local/bin", "$home/.cargo/bin", "/usr/local/bin", "/usr/bin", "/opt/homebrew/bin"))
            .mapNotNull { runCatching { Path.of(it, name) }.getOrNull() }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    }
}

class QlangConfigurable : BoundSearchableConfigurable("qlang", "dev.qlang.jetbrains.settings") {
    private val settings get() = QlangSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row("Exécutable qlang :") {
            textFieldWithBrowseButton(
                FileChooserDescriptorFactory.singleFile().withTitle("Exécutable qlang"),
            ).align(AlignX.FILL).bindText(settings::executablePath)
                .comment("Laisser vide pour chercher <code>qlang</code> dans le PATH.")
        }
        row {
            comment("Le serveur est lancé par <code>qlang lsp</code>. Après un changement, rouvrir le projet ou redémarrer le serveur.")
        }
    }

    override fun apply() {
        super.apply()
        // le serveur est lancé avec l'ancien chemin : le redémarrer pour les projets ouverts
        QlangLspRestarter.restartAll()
    }
}
