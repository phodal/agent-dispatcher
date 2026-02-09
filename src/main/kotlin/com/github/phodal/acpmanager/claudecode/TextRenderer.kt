package com.github.phodal.acpmanager.claudecode

import com.github.phodal.acpmanager.ui.renderer.AcpEventRenderer
import com.github.phodal.acpmanager.ui.renderer.PlanEntryStatus
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import javax.swing.JPanel

/**
 * A pure text renderer for testing and CLI usage.
 * Collects all render events and outputs them as plain text.
 *
 * This renderer does not use any Swing components and can be used
 * in headless environments for E2E testing.
 */
class TextRenderer(
    private val agentKey: String = "test",
    private val output: (String) -> Unit = ::println,
) : AcpEventRenderer {

    override val container: JPanel = JPanel() // Dummy panel for interface compliance

    private val events = mutableListOf<RenderEvent>()
    private val thinkingBuffer = StringBuilder()
    private val messageBuffer = StringBuilder()

    val capturedEvents: List<RenderEvent> get() = events.toList()
    val capturedOutput: String get() = outputBuffer.toString()

    private val outputBuffer = StringBuilder()

    override fun onEvent(event: RenderEvent) {
        events.add(event)
        val line = formatEvent(event)
        if (line.isNotEmpty()) {
            outputBuffer.appendLine(line)
            output(line)
        }
    }

    private fun formatEvent(event: RenderEvent): String {
        return when (event) {
            is RenderEvent.UserMessage -> "[USER] ${event.content}"
            is RenderEvent.ThinkingStart -> "[THINKING] ..."
            is RenderEvent.ThinkingChunk -> {
                thinkingBuffer.append(event.content)
                "" // Don't output chunks, wait for end
            }
            is RenderEvent.ThinkingEnd -> {
                val content = event.fullContent.take(200)
                thinkingBuffer.clear()
                "[THINKING] $content${if (event.fullContent.length > 200) "..." else ""}"
            }
            is RenderEvent.MessageStart -> {
                messageBuffer.clear()
                ""
            }
            is RenderEvent.MessageChunk -> {
                messageBuffer.append(event.content)
                "" // Don't output chunks, wait for end
            }
            is RenderEvent.MessageEnd -> {
                val content = event.fullContent
                messageBuffer.clear()
                "[ASSISTANT] $content"
            }
            is RenderEvent.ToolCallStart -> "[TOOL:START] ${event.toolName}: ${event.title ?: ""}"
            is RenderEvent.ToolCallUpdate -> "[TOOL:UPDATE] ${event.toolCallId}: ${event.status}"
            is RenderEvent.ToolCallEnd -> {
                val output = event.output?.take(100) ?: ""
                "[TOOL:END] ${event.toolCallId}: ${event.status} - $output"
            }
            is RenderEvent.PlanUpdate -> {
                val entries = event.entries.mapIndexed { i, e ->
                    val marker = when (e.status) {
                        PlanEntryStatus.COMPLETED -> "[x]"
                        PlanEntryStatus.IN_PROGRESS -> "[*]"
                        PlanEntryStatus.PENDING -> "[ ]"
                    }
                    "${i + 1}. $marker ${e.content}"
                }.joinToString("\n")
                "[PLAN]\n$entries"
            }
            is RenderEvent.ModeChange -> "[MODE] ${event.modeId}"
            is RenderEvent.Info -> "[INFO] ${event.message}"
            is RenderEvent.Error -> "[ERROR] ${event.message}"
            is RenderEvent.Connected -> "[CONNECTED] ${event.agentKey}"
            is RenderEvent.Disconnected -> "[DISCONNECTED] ${event.agentKey}"
            is RenderEvent.PromptComplete -> "[COMPLETE] ${event.stopReason ?: "done"}"
        }
    }

    override fun clear() {
        events.clear()
        thinkingBuffer.clear()
        messageBuffer.clear()
        outputBuffer.clear()
    }

    override fun dispose() {
        clear()
    }

    override fun scrollToBottom() {
        // No-op for text renderer
    }

    override fun getDebugState(): String {
        return "TextRenderer[$agentKey](events=${events.size})"
    }

    /**
     * Get a summary of captured events for assertions.
     */
    fun getSummary(): TextRendererSummary {
        return TextRendererSummary(
            userMessages = events.filterIsInstance<RenderEvent.UserMessage>().map { it.content },
            assistantMessages = events.filterIsInstance<RenderEvent.MessageEnd>().map { it.fullContent },
            thinkingMessages = events.filterIsInstance<RenderEvent.ThinkingEnd>().map { it.fullContent },
            toolCalls = events.filterIsInstance<RenderEvent.ToolCallStart>().map { it.toolName to it.title },
            toolResults = events.filterIsInstance<RenderEvent.ToolCallEnd>().map { it.toolCallId to it.status },
            errors = events.filterIsInstance<RenderEvent.Error>().map { it.message },
            isConnected = events.any { it is RenderEvent.Connected },
            isComplete = events.any { it is RenderEvent.PromptComplete },
        )
    }
}

/**
 * Summary of captured events for easy assertions in tests.
 */
data class TextRendererSummary(
    val userMessages: List<String>,
    val assistantMessages: List<String>,
    val thinkingMessages: List<String>,
    val toolCalls: List<Pair<String, String?>>,
    val toolResults: List<Pair<String, com.agentclientprotocol.model.ToolCallStatus>>,
    val errors: List<String>,
    val isConnected: Boolean,
    val isComplete: Boolean,
)

