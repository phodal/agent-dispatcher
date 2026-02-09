package com.github.phodal.acpmanager.claudecode

import com.github.phodal.acpmanager.ui.renderer.AcpEventRenderer
import com.github.phodal.acpmanager.ui.renderer.AcpEventRendererFactory
import com.github.phodal.acpmanager.ui.renderer.AcpEventRendererRegistry

/**
 * Factory for creating ClaudeCodeRenderer instances.
 */
class ClaudeCodeRendererFactory : AcpEventRendererFactory {
    override fun createRenderer(agentKey: String, scrollCallback: () -> Unit): AcpEventRenderer {
        return ClaudeCodeRenderer(agentKey, scrollCallback)
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

