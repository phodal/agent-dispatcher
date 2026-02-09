package com.github.phodal.acpmanager.ui.renderer

import com.agentclientprotocol.model.ToolCallStatus
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

private val log = logger<DefaultAcpEventRenderer>()

/**
 * Default implementation of AcpEventRenderer.
 * Renders ACP events as a vertical list of message panels.
 */
class DefaultAcpEventRenderer(
    private val agentKey: String,
    private val scrollCallback: () -> Unit,
) : AcpEventRenderer {

    override val container: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()
    }

    // Streaming state
    private var currentThinkingPanel: StreamingPanel? = null
    private var currentMessagePanel: StreamingPanel? = null
    private val thinkingBuffer = StringBuilder()
    private val messageBuffer = StringBuilder()

    // Tool call tracking
    private val toolCallPanels = mutableMapOf<String, ToolCallPanel>()

    override fun onEvent(event: RenderEvent) {
        log.info("DefaultRenderer[$agentKey]: onEvent ${event::class.simpleName}")
        when (event) {
            is RenderEvent.UserMessage -> addUserMessage(event)
            is RenderEvent.ThinkingStart -> startThinking()
            is RenderEvent.ThinkingChunk -> appendThinking(event.content)
            is RenderEvent.ThinkingEnd -> endThinking(event.fullContent)
            is RenderEvent.MessageStart -> startMessage()
            is RenderEvent.MessageChunk -> appendMessage(event.content)
            is RenderEvent.MessageEnd -> endMessage(event.fullContent)
            is RenderEvent.ToolCallStart -> startToolCall(event)
            is RenderEvent.ToolCallUpdate -> updateToolCall(event)
            is RenderEvent.ToolCallEnd -> endToolCall(event)
            is RenderEvent.PlanUpdate -> addPlanUpdate(event)
            is RenderEvent.ModeChange -> addInfo("Mode: ${event.modeId}", event.timestamp)
            is RenderEvent.Info -> addInfo(event.message, event.timestamp)
            is RenderEvent.Error -> addError(event.message, event.timestamp)
            is RenderEvent.Connected -> addInfo("Connected to '$agentKey'", event.timestamp)
            is RenderEvent.Disconnected -> addInfo("Disconnected from '$agentKey'", event.timestamp)
            is RenderEvent.PromptComplete -> finalizeStreaming()
        }
        scrollCallback()
    }

    private fun addUserMessage(event: RenderEvent.UserMessage) {
        val panel = createMessagePanel("You", event.content, event.timestamp,
            JBColor(Color(0x1565C0), Color(0x64B5F6)),
            JBColor(Color(0xE3F2FD), Color(0x1A3A5C)))
        addPanel(panel)
    }

    private fun startThinking() {
        if (currentThinkingPanel == null) {
            thinkingBuffer.clear()
            currentThinkingPanel = StreamingPanel("💡 Thinking...",
                JBColor(Color(0x6A1B9A), Color(0xCE93D8)),
                JBColor(Color(0xF3E5F5), Color(0x2A1A2E)))
            addPanel(currentThinkingPanel!!)
        }
    }

    private fun appendThinking(content: String) {
        thinkingBuffer.append(content)
        currentThinkingPanel?.updateContent(thinkingBuffer.toString())
    }

    private fun endThinking(fullContent: String) {
        currentThinkingPanel?.let { container.remove(it) }
        currentThinkingPanel = null
        if (fullContent.isNotBlank()) {
            val panel = createThinkingPanel(fullContent)
            addPanel(panel)
        }
        thinkingBuffer.clear()
    }

    private fun startMessage() {
        // Finalize thinking first if still active
        if (currentThinkingPanel != null) {
            endThinking(thinkingBuffer.toString())
        }
        if (currentMessagePanel == null) {
            messageBuffer.clear()
            currentMessagePanel = StreamingPanel("Assistant (typing...)",
                JBColor(Color(0x2E7D32), Color(0x81C784)),
                JBColor(Color(0xF5F5F5), Color(0x2B2B2B)))
            addPanel(currentMessagePanel!!)
        }
    }

    private fun appendMessage(content: String) {
        if (currentMessagePanel == null) startMessage()
        messageBuffer.append(content)
        currentMessagePanel?.updateContent(messageBuffer.toString())
    }

    private fun endMessage(fullContent: String) {
        currentMessagePanel?.let { container.remove(it) }
        currentMessagePanel = null
        if (fullContent.isNotBlank()) {
            val panel = createMessagePanel("Assistant", fullContent, System.currentTimeMillis(),
                JBColor(Color(0x2E7D32), Color(0x81C784)),
                JBColor(Color(0xF5F5F5), Color(0x2B2B2B)))
            addPanel(panel)
        }
        messageBuffer.clear()
    }

    private fun startToolCall(event: RenderEvent.ToolCallStart) {
        val panel = ToolCallPanel(event.toolCallId, event.title ?: event.toolName)
        toolCallPanels[event.toolCallId] = panel
        addPanel(panel)
    }

    private fun updateToolCall(event: RenderEvent.ToolCallUpdate) {
        toolCallPanels[event.toolCallId]?.updateStatus(event.status, event.title)
    }

    private fun endToolCall(event: RenderEvent.ToolCallEnd) {
        toolCallPanels[event.toolCallId]?.complete(event.status, event.output)
        toolCallPanels.remove(event.toolCallId)
    }

    private fun addPlanUpdate(event: RenderEvent.PlanUpdate) {
        val text = buildString {
            event.entries.forEachIndexed { i, entry ->
                val marker = when (entry.status) {
                    PlanEntryStatus.COMPLETED -> "[x]"
                    PlanEntryStatus.IN_PROGRESS -> "[*]"
                    PlanEntryStatus.PENDING -> "[ ]"
                }
                appendLine("${i + 1}. $marker ${entry.content}")
            }
        }.trim()
        if (text.isNotBlank()) addInfo("Plan:\n$text", event.timestamp)
    }

    private fun finalizeStreaming() {
        if (currentThinkingPanel != null) endThinking(thinkingBuffer.toString())
        if (currentMessagePanel != null) endMessage(messageBuffer.toString())
    }

    private fun addInfo(message: String, timestamp: Long) {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(Color(0xE0F7FA), Color(0x1A2F33))
            border = JBUI.Borders.empty(3, 10)
            val label = JBLabel("ℹ $message").apply {
                foreground = JBColor(Color(0x00695C), Color(0x80CBC4))
                font = font.deriveFont(Font.ITALIC, font.size2D - 1)
            }
            add(label, BorderLayout.CENTER)
        }
        addPanel(panel)
    }

    private fun addError(message: String, timestamp: Long) {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(Color(0xFFEBEE), Color(0x3A1A1A))
            border = JBUI.Borders.empty(4, 10)
            val label = JBLabel("⚠ $message").apply {
                foreground = JBColor(Color(0xC62828), Color(0xEF9A9A))
            }
            add(label, BorderLayout.CENTER)
        }
        addPanel(panel)
    }

    private fun createMessagePanel(
        name: String, content: String, timestamp: Long,
        headerColor: Color, bgColor: Color
    ): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = bgColor
            border = JBUI.Borders.empty(4, 8)

            val wrapper = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(6, 10)
            }

            val header = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyBottom(4)
                add(JBLabel(name).apply {
                    foreground = headerColor
                    font = font.deriveFont(Font.BOLD)
                }, BorderLayout.WEST)
                add(JBLabel(SimpleDateFormat("HH:mm:ss").format(Date(timestamp))).apply {
                    foreground = UIUtil.getLabelDisabledForeground()
                    font = font.deriveFont(font.size2D - 2)
                }, BorderLayout.EAST)
            }
            wrapper.add(header, BorderLayout.NORTH)

            val textArea = JTextArea(content).apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                font = UIUtil.getLabelFont()
                foreground = UIUtil.getLabelForeground()
            }
            wrapper.add(textArea, BorderLayout.CENTER)
            add(wrapper, BorderLayout.CENTER)
        }
    }

    private fun createThinkingPanel(content: String): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(Color(0xF3E5F5), Color(0x2A1A2E))
            border = JBUI.Borders.empty(4, 8)

            val wrapper = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(4, 10)
            }

            val header = JBLabel("💡 Thinking").apply {
                foreground = JBColor(Color(0x6A1B9A), Color(0xCE93D8))
                font = font.deriveFont(Font.ITALIC, font.size2D - 1)
            }
            wrapper.add(header, BorderLayout.NORTH)

            val displayContent = if (content.length > 300) content.take(300) + "..." else content
            val textArea = JTextArea(displayContent).apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                font = UIUtil.getLabelFont().deriveFont(Font.ITALIC)
                foreground = JBColor(Color(0x6A1B9A), Color(0xCE93D8))
            }
            wrapper.add(textArea, BorderLayout.CENTER)
            add(wrapper, BorderLayout.CENTER)
        }
    }

    private fun addPanel(panel: JPanel) {
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height)
        panel.alignmentX = Component.LEFT_ALIGNMENT
        container.add(panel)
        container.add(Box.createVerticalStrut(2))
        container.revalidate()
        container.repaint()
    }

    override fun clear() {
        container.removeAll()
        currentThinkingPanel = null
        currentMessagePanel = null
        thinkingBuffer.clear()
        messageBuffer.clear()
        toolCallPanels.clear()
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
        return "DefaultRenderer[$agentKey](panels=${container.componentCount}, " +
                "thinking=${currentThinkingPanel != null}, message=${currentMessagePanel != null})"
    }
}

