package dev.qlang.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState

fun isQlangServerRunning(project: Project): Boolean =
    LspClientManager.getInstance(project).getClients(QlangLspServerSupportProvider::class.java)
        .any { it.state == LspServerState.Running }
