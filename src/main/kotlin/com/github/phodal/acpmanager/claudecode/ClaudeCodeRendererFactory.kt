package com.github.phodal.acpmanager.claudecode

import com.github.phodal.acpmanager.ui.renderer.AcpEventRenderer
import com.github.phodal.acpmanager.ui.renderer.AcpEventRendererFactory
import com.github.phodal.acpmanager.ui.renderer.AcpEventRendererRegistry
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.openapi.project.Project

/**
 * Factory for creating ClaudeCodeRenderer instances.
 */
class ClaudeCodeRendererFactory : AcpEventRendererFactory {
    override fun createRenderer(
        agentKey: String,
        scrollCallback: () -> Unit,
        project: Project?,
        eventCallback: ((RenderEvent) -> Unit)?
    ): AcpEventRenderer {
        return ClaudeCodeRenderer(agentKey, scrollCallback, project, eventCallback)
    }
}

/**
 * Register the Claude Code renderer factory.
 * Call this during plugin initialization.
 */
fun registerClaudeCodeRenderer() {
    AcpEventRendererRegistry.registerFactory("claude-code", ClaudeCodeRendererFactory())
    AcpEventRendererRegistry.registerFactory("claude", ClaudeCodeRendererFactory())
}

