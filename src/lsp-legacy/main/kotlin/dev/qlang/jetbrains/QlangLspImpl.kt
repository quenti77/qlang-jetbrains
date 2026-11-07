package dev.qlang.jetbrains

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import org.eclipse.lsp4j.services.LanguageServer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

// Variante « legacy » : API LSP de PhpStorm 2025.3 à 2026.1 (LspServer, LspServerSupportProvider…).

class QlangLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (!file.isQlang()) return
        val exe = resolveOrNotify(project) ?: return
        serverStarter.ensureServerStarted(QlangLspServerDescriptor(project, exe))
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
            LspServerManager.getInstance(project).stopAndRestartIfNeeded(QlangLspServerSupportProvider::class.java)
        }
    }
}

private class LegacyServer(private val server: LspServer) : QlangServer {
    override fun fileUri(file: VirtualFile) = server.descriptor.getFileUri(file)
    override fun <T> request(timeoutMs: Int, call: (LanguageServer) -> CompletableFuture<T>): T? =
        server.sendRequestSync(timeoutMs) { call(it) }
}

/** Le serveur qlang de ce projet qui s'occupe de ce fichier, s'il tourne. */
fun qlangServer(project: Project, file: VirtualFile): QlangServer? =
    LspServerManager.getInstance(project).getServersForProvider(QlangLspServerSupportProvider::class.java)
        .firstOrNull { it.descriptor.isSupportedFile(file) }
        ?.let { LegacyServer(it) }
