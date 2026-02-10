package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.claudecode.registerClaudeCodeRenderer
import com.github.phodal.acpmanager.dispatcher.ui.DispatcherPanel
import com.github.phodal.acpmanager.ui.renderer.initializeDefaultRendererFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for the ACP Manager tool window.
 *
 * Creates the main panel that provides:
 * - Multi-agent session management (Chat tab)
 * - Multi-agent dispatcher (Dispatcher tab)
 * - Chat interface per agent
 * - Agent configuration
 */
class AcpToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        @Volatile
        private var renderersInitialized = false

        /**
         * Initialize renderer factories. Called once on first tool window creation.
         */
        @Synchronized
        private fun initializeRenderers() {
            if (renderersInitialized) return
            initializeDefaultRendererFactory()
            registerClaudeCodeRenderer()
            renderersInitialized = true
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Initialize renderers on first use
        initializeRenderers()

        // Tab 1: ACP Manager (existing chat panel)
        val chatPanel = AcpManagerPanel(project)
        val chatContent = ContentFactory.getInstance().createContent(chatPanel, "Single", false)
        Disposer.register(chatContent, chatPanel)
        toolWindow.contentManager.addContent(chatContent)

        // Tab 2: Multi-Agent Dispatcher
        val dispatcherPanel = DispatcherPanel(project)
        val dispatcherContent = ContentFactory.getInstance().createContent(dispatcherPanel, "Dispatcher", false)
        Disposer.register(dispatcherContent, dispatcherPanel)
        toolWindow.contentManager.addContent(dispatcherContent)
    }

    override fun shouldBeAvailable(project: Project) = true
}
