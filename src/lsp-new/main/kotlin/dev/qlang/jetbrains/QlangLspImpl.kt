package dev.qlang.jetbrains

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import org.eclipse.lsp4j.services.LanguageServer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

// Variante « new » : API LSP de PhpStorm 2026.2+ (LspClient, LspIntegrationProvider…).

class QlangLspServerSupportProvider : LspIntegrationProvider {
    override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
        if (!file.isQlang()) return
        val exe = resolveOrNotify(project) ?: return
        clientStarter.ensureClientStarted(QlangLspServerDescriptor(project, exe))
    }
}

class QlangLspServerDescriptor(project: Project, private val exe: Path) :
    ProjectWideLspClientDescriptor(project, "qlang") {

    override fun isSupportedFile(file: VirtualFile) = file.isQlang()

    override fun getLanguageId(file: VirtualFile) = "qlang"

    override fun createCommandLine(): GeneralCommandLine =
        GeneralCommandLine(exe.toString(), "lsp")
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(project.basePath)

    // Reformat Code = textDocument/formatting (qlang fmt), pour tous les fichiers .q
    override val lspCustomization = object : LspCustomization() {
        override val formattingCustomizer = object : LspFormattingSupport() {
            override fun shouldFormatThisFileExclusivelyByServer(
                file: VirtualFile,
                ideCanFormatThisFileItself: Boolean,
                serverExplicitlyWantsToFormatThisFile: Boolean,
            ) = file.isQlang()
        }
    }

    override fun equals(other: Any?) = other is QlangLspServerDescriptor && other.project == project && other.exe == exe
    override fun hashCode() = 31 * project.hashCode() + exe.hashCode()
}

object QlangLspRestarter {
    fun restartAll() {
        qlangMissingWarned.set(false)
        for (project in ProjectManager.getInstance().openProjects) {
            LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(QlangLspServerSupportProvider::class.java)
        }
    }
}

private class NewServer(private val server: LspClient) : QlangServer {
    override fun fileUri(file: VirtualFile) = server.descriptor.getFileUri(file)
    override fun <T> request(timeoutMs: Int, call: (LanguageServer) -> CompletableFuture<T>): T? =
        server.sendRequestSync(timeoutMs) { call(it) }
}

/** Le serveur qlang de ce projet qui s'occupe de ce fichier, s'il tourne. */
fun qlangServer(project: Project, file: VirtualFile): QlangServer? =
    LspClientManager.getInstance(project).getClients(QlangLspServerSupportProvider::class.java)
        .firstOrNull { it.descriptor.isSupportedFile(file) }
        ?.let { NewServer(it) }
