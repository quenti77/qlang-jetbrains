package dev.qlang.jetbrains

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

fun VirtualFile.isQlang() = extension == "q"

/**
 * Ce que le reste du plugin sait faire du serveur qlang. L'API LSP de la plateforme a changé de noms en 2026.1.4
 * (LspServer -> LspClient…) : chaque variante de compilation (`src/lsp-legacy`, `src/lsp-new`) l'implémente.
 */
interface QlangServer {
    fun fileUri(file: VirtualFile): String
    fun <T> request(timeoutMs: Int, call: (LanguageServer) -> CompletableFuture<T>): T?
}

/** Un seul message par session de l'IDE tant que rien ne change. */
val qlangMissingWarned = AtomicBoolean(false)

/** Résout l'exécutable ; sinon prévient l'utilisateur (une fois) et rend null. */
fun resolveOrNotify(project: Project): java.nio.file.Path? {
    QlangExecutable.resolve()?.let { return it }
    if (!qlangMissingWarned.compareAndSet(false, true)) return null
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
    return null
}
