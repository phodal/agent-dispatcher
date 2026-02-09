package com.github.phodal.acpmanager.ui.renderer

import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Abstract interface for rendering ACP events to UI components.
 *
 * Different agents (e.g., ClaudeCode, Codex, Gemini) can provide their own
 * implementations to customize how events are displayed.
 *
 * The renderer maintains a container panel and updates it based on incoming events.
 * It supports:
 * - Streaming content (thinking, message chunks)
 * - Tool call lifecycle (start, update, end)
 * - Plan updates
 * - Status messages
 */
interface AcpEventRenderer {

    /**
     * The container panel that holds all rendered content.
     * This panel should be added to the chat UI.
     */
    val container: JPanel

    /**
     * Process a render event and update the UI accordingly.
     *
     * @param event The render event to process
     */
    fun onEvent(event: RenderEvent)

    /**
     * Clear all rendered content.
     */
    fun clear()

    /**
     * Called when the renderer is being disposed.
     */
    fun dispose()

    /**
     * Scroll to the bottom of the content.
     * Called after content updates to keep the latest content visible.
     */
    fun scrollToBottom()

    /**
     * Get the current state of the renderer for debugging/logging.
     */
    fun getDebugState(): String {
        return "AcpEventRenderer(components=${container.componentCount})"
    }
}

/**
 * Factory for creating AcpEventRenderer instances.
 * Allows different agents to register their own renderer implementations.
 */
interface AcpEventRendererFactory {
    /**
     * Create a renderer for the given agent.
     *
     * @param agentKey The agent identifier
     * @param scrollCallback Callback to trigger scroll to bottom
     * @return A new renderer instance
     */
    fun createRenderer(agentKey: String, scrollCallback: () -> Unit): AcpEventRenderer
}

/**
 * Registry for renderer factories.
 * Agents can register custom factories for specific agent types.
 */
object AcpEventRendererRegistry {
    private val factories = mutableMapOf<String, AcpEventRendererFactory>()
    private var defaultFactory: AcpEventRendererFactory? = null

    /**
     * Register a factory for a specific agent type.
     */
    fun registerFactory(agentType: String, factory: AcpEventRendererFactory) {
        factories[agentType] = factory
    }

    /**
     * Set the default factory used when no specific factory is registered.
     */
    fun setDefaultFactory(factory: AcpEventRendererFactory) {
        defaultFactory = factory
    }

    /**
     * Get a factory for the given agent type.
     * Falls back to default factory if no specific factory is registered.
     */
    fun getFactory(agentType: String): AcpEventRendererFactory? {
        return factories[agentType] ?: defaultFactory
    }

    /**
     * Create a renderer for the given agent.
     */
    fun createRenderer(agentKey: String, agentType: String, scrollCallback: () -> Unit): AcpEventRenderer {
        val factory = getFactory(agentType)
            ?: throw IllegalStateException("No renderer factory registered for agent type: $agentType")
        return factory.createRenderer(agentKey, scrollCallback)
    }
}

