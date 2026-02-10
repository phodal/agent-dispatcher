package com.github.phodal.acpmanager.claudecode

import com.github.phodal.acpmanager.claudecode.context.RenderContext
import com.github.phodal.acpmanager.claudecode.handlers.*
import com.github.phodal.acpmanager.ui.renderer.AcpEventRenderer
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JPanel

private val log = logger<ClaudeCodeRenderer>()

/**
 * Custom renderer for Claude Code with Claude-specific styling.
 *
 * Features:
 * - Modular event handling via strategy pattern
 * - Collapsible tool call and task panels
 * - Unified background colors
 * - Easy to extend with new event handlers
 * - Event emission for external UI components (e.g., task status above input)
 *
 * Architecture:
 * - RenderContext: Shared state and utilities
 * - RenderEventHandler: Strategy interface for event processing
 * - RenderPanel: Base interface for UI panels
 */
class ClaudeCodeRenderer(
    private val agentKey: String,
    private val scrollCallback: () -> Unit,
    private val project: Project? = null,
    private val eventCallback: ((RenderEvent) -> Unit)? = null,
) : AcpEventRenderer {

    // Inner content panel that holds actual messages
    private val contentPanel: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    override val container: JPanel = JPanel(BorderLayout()).apply {
        background = UIUtil.getPanelBackground()
        add(contentPanel, BorderLayout.NORTH) // NORTH ensures content stays at top
    }

    // Shared context for all handlers
    private val context = RenderContext(contentPanel, scrollCallback, agentKey, project, eventCallback)

    // Task handler needs special handling for clear()
    private val taskHandler = TaskEventHandler()

    // Event handlers - order matters! Task handler must come before ToolCall handler
    private val handlers: List<RenderEventHandler> = listOf(
        ThinkingEventHandler(),
        MessageEventHandler(),
        taskHandler,           // Must be before ToolCallEventHandler
        ToolCallEventHandler(),
        StatusEventHandler()
    )

    override fun onEvent(event: RenderEvent) {
        log.info("ClaudeCodeRenderer[$agentKey]: onEvent ${event::class.simpleName}")

        // Find and execute the appropriate handler
        val handler = handlers.firstOrNull { it.canHandle(event) }
        if (handler != null) {
            handler.handle(event, context)
        } else {
            log.warn("ClaudeCodeRenderer[$agentKey]: No handler found for ${event::class.simpleName}")
        }
    }

    override fun clear() {
        context.clear()
        taskHandler.clear()
        container.revalidate()
        container.repaint()
    }

    override fun dispose() {
        clear()
    }

    override fun scrollToBottom() {
        scrollCallback()
    }

    override fun getDebugState(): String {
        return "ClaudeCodeRenderer[$agentKey](panels=${contentPanel.componentCount}, registry=${context.panelRegistry.size()})"
    }
}

