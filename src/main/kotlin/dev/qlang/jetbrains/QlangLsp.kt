package dev.qlang.jetbrains

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import java.nio.charset.StandardCharsets
import java.nio.file.Path

fun VirtualFile.isQlang() = extension == "q"

class QlangLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (!file.isQlang()) return
        val exe = QlangExecutable.resolve()
        if (exe == null) {
            notifyMissing(project)
            return
        }
        serverStarter.ensureServerStarted(QlangLspServerDescriptor(project, exe))
    }

    private fun notifyMissing(project: Project) {
        if (!warned.compareAndSet(false, true)) return
        val configured = QlangSettings.getInstance().executablePath
        val text = if (configured.isBlank()) {
            "L'exécutable <code>qlang</code> est introuvable dans le PATH : le serveur de langage n'est pas lancé " +
                "(ni erreurs, ni complétion, ni <code>end</code> sur Entrée). Installez qlang ou indiquez son chemin."
        } else {
            "Le fichier « $configured » n'existe pas ou n'est pas exécutable : le serveur de langage n'est pas lancé."
        }
        NotificationGroupManager.getInstance().getNotificationGroup("qlang")
            .createNotification("qlang introuvable", text, NotificationType.WARNING)
            .addAction(NotificationAction.createSimple("Ouvrir les réglages") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, QlangConfigurable::class.java)
            })
            .addAction(NotificationAction.createSimple("Installer qlang") {
                com.intellij.ide.BrowserUtil.browse("https://github.com/quenti77/qlang#installation")
            })
            .notify(project)
    }

    companion object {
        /** Un seul message par session de l'IDE tant que rien ne change. */
        val warned = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}

class QlangLspServerDescriptor(project: Project, private val exe: Path) :
    ProjectWideLspServerDescriptor(project, "qlang") {

    override fun isSupportedFile(file: VirtualFile) = file.isQlang()

    override fun getLanguageId(file: VirtualFile) = "qlang"

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine(exe.toString(), "lsp")
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(project.basePath)

    // Reformat Code = textDocument/formatting (qlang fmt), pour tous les fichiers .q
    override val lspFormattingSupport = object : LspFormattingSupport() {
        override fun shouldFormatThisFileExclusivelyByServer(
            file: VirtualFile,
            ideCanFormatThisFileItself: Boolean,
            serverExplicitlyWantsToFormatThisFile: Boolean,
        ) = file.isQlang()
    }

    override fun equals(other: Any?) = other is QlangLspServerDescriptor && other.project == project && other.exe == exe
    override fun hashCode() = 31 * project.hashCode() + exe.hashCode()
}

object QlangLspRestarter {
    fun restartAll() {
        QlangLspServerSupportProvider.warned.set(false)
        for (project in ProjectManager.getInstance().openProjects) {
            @Suppress("UnstableApiUsage")
            LspServerManager.getInstance(project).stopAndRestartIfNeeded(QlangLspServerSupportProvider::class.java)
        }
    }
}

/** Le serveur qlang de ce projet qui s'occupe de ce fichier, s'il tourne. */
fun qlangServer(project: Project, file: VirtualFile): LspServer? =
    LspServerManager.getInstance(project).getServersForProvider(QlangLspServerSupportProvider::class.java)
        .firstOrNull { it.descriptor.isSupportedFile(file) }
