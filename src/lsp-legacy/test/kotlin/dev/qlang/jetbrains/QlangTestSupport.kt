package dev.qlang.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState

fun isQlangServerRunning(project: Project): Boolean =
    LspServerManager.getInstance(project).getServersForProvider(QlangLspServerSupportProvider::class.java)
        .any { it.state == LspServerState.Running }
