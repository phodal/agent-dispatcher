package com.github.phodal.acpmanager.claudecode

import com.agentclientprotocol.model.ToolCallStatus
import com.github.phodal.acpmanager.ui.renderer.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

private val log = logger<ClaudeCodeRenderer>()

/**
 * Custom renderer for Claude Code with Claude-specific styling.
 *
 * Features:
 * - Orange/amber accent colors for Claude branding
 * - Enhanced tool call display with parameter details
 * - Compact thinking display
 */
class ClaudeCodeRenderer(
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

    // Claude Code colors
    private val claudeAccent = JBColor(Color(0xD97706), Color(0xFBBF24)) // Amber
    private val claudeThinkingBg = JBColor(Color(0xFEF3C7), Color(0x422006))
    private val claudeThinkingFg = JBColor(Color(0x92400E), Color(0xFCD34D))
    private val claudeMessageBg = JBColor(Color(0xFFF7ED), Color(0x1C1917))
    private val claudeMessageFg = JBColor(Color(0x9A3412), Color(0xFDBA74))

    override fun onEvent(event: RenderEvent) {
        log.info("ClaudeCodeRenderer[$agentKey]: onEvent ${event::class.simpleName}")
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
            is RenderEvent.ModeChange -> addModeChange(event)
            is RenderEvent.Info -> addInfo(event)
            is RenderEvent.Error -> addError(event)
            is RenderEvent.Connected -> addConnected(event)
            is RenderEvent.Disconnected -> addDisconnected(event)
            is RenderEvent.PromptComplete -> onPromptComplete(event)
        }
    }

    private fun addUserMessage(event: RenderEvent.UserMessage) {
        val panel = createMessagePanel(
            "You", event.content, event.timestamp,
            JBColor.BLUE, JBColor(Color(0xE3F2FD), Color(0x1A237E))
        )
        addPanel(panel)
        scrollCallback()
    }

    private fun startThinking() {
        thinkingBuffer.clear()
        val panel = StreamingPanel(
            "🧠 Claude is thinking...",
            claudeThinkingFg,
            claudeThinkingBg
        )
        currentThinkingPanel = panel
        addPanel(panel)
        scrollCallback()
    }

    private fun appendThinking(content: String) {
        thinkingBuffer.append(content)
        val display = if (thinkingBuffer.length > 200) {
            thinkingBuffer.takeLast(200).toString() + "..."
        } else {
            thinkingBuffer.toString()
        }
        currentThinkingPanel?.updateContent(display)
        scrollCallback()
    }

    private fun endThinking(fullContent: String) {
        currentThinkingPanel?.let { panel ->
            container.remove(panel)
            val finalPanel = createThinkingPanel(fullContent)
            addPanel(finalPanel)
        }
        currentThinkingPanel = null
        thinkingBuffer.clear()
        scrollCallback()
    }

    private fun startMessage() {
        messageBuffer.clear()
        val panel = StreamingPanel(
            "Claude",
            claudeAccent,
            claudeMessageBg
        )
        currentMessagePanel = panel
        addPanel(panel)
        scrollCallback()
    }

    private fun appendMessage(content: String) {
        messageBuffer.append(content)
        currentMessagePanel?.updateContent(messageBuffer.toString())
        scrollCallback()
    }

    private fun endMessage(fullContent: String) {
        currentMessagePanel?.let { panel ->
            container.remove(panel)
            val finalPanel = createMessagePanel(
                "Claude", fullContent, System.currentTimeMillis(),
                claudeAccent, claudeMessageBg
            )
            addPanel(finalPanel)
        }
        currentMessagePanel = null
        messageBuffer.clear()
        scrollCallback()
    }

    private fun startToolCall(event: RenderEvent.ToolCallStart) {
        val panel = ToolCallPanel(event.toolName, event.title ?: event.toolName)
        toolCallPanels[event.toolCallId] = panel
        addPanel(panel)
        scrollCallback()
    }

    private fun updateToolCall(event: RenderEvent.ToolCallUpdate) {
        toolCallPanels[event.toolCallId]?.updateStatus(event.status, event.title)
        scrollCallback()
    }

    private fun endToolCall(event: RenderEvent.ToolCallEnd) {
        toolCallPanels[event.toolCallId]?.complete(event.status, event.output)
        scrollCallback()
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
        if (text.isNotBlank()) addInfoPanel("📋 Plan:\n$text", claudeAccent)
        scrollCallback()
    }

    private fun addModeChange(event: RenderEvent.ModeChange) {
        addInfoPanel("Mode: ${event.modeId}", claudeAccent)
    }

    private fun addInfo(event: RenderEvent.Info) {
        addInfoPanel(event.message, UIUtil.getLabelDisabledForeground())
    }

    private fun addError(event: RenderEvent.Error) {
        addInfoPanel("⚠️ ${event.message}", JBColor.RED)
    }

    private fun addConnected(event: RenderEvent.Connected) {
        addInfoPanel("🔗 Connected to Claude Code", claudeAccent)
    }

    private fun addDisconnected(event: RenderEvent.Disconnected) {
        addInfoPanel("🔌 Disconnected from Claude Code", UIUtil.getLabelDisabledForeground())
    }

    private fun onPromptComplete(event: RenderEvent.PromptComplete) {
        log.info("ClaudeCodeRenderer[$agentKey]: Prompt complete (${event.stopReason})")
    }

    private fun addInfoPanel(text: String, color: Color) {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 8)
            add(JBLabel(text).apply {
                foreground = color
                font = font.deriveFont(font.size2D - 1)
            }, BorderLayout.WEST)
        }
        addPanel(panel)
    }

    private fun createMessagePanel(
        name: String, content: String, timestamp: Long,
        headerColor: Color, bgColor: Color
    ): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val parentWidth = parent?.width ?: 400
                val pref = super.getPreferredSize()
                return Dimension(parentWidth, pref.height)
            }
        }.apply {
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

            val textArea = createWrappingTextArea(content)
            wrapper.add(textArea, BorderLayout.CENTER)
            add(wrapper, BorderLayout.CENTER)
        }
    }

    private fun createWrappingTextArea(content: String, foregroundColor: Color? = null): JTextArea {
        return object : JTextArea(content) {
            override fun getPreferredSize(): Dimension {
                val parentWidth = parent?.parent?.parent?.width ?: parent?.parent?.width ?: parent?.width ?: 400
                val availableWidth = maxOf(100, parentWidth - 50)
                val fm = getFontMetrics(font)
                val lines = if (availableWidth > 0 && text.isNotEmpty()) {
                    var lineCount = 0
                    for (line in text.split("\n")) {
                        if (line.isEmpty()) lineCount++ else {
                            val lineWidth = fm.stringWidth(line)
                            lineCount += maxOf(1, (lineWidth + availableWidth - 1) / availableWidth)
                        }
                    }
                    maxOf(1, lineCount)
                } else 1
                return Dimension(availableWidth, lines * fm.height + 4)
            }
        }.apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont()
            foreground = foregroundColor ?: UIUtil.getLabelForeground()
        }
    }

    private fun createThinkingPanel(content: String): JPanel {
        return object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val parentWidth = parent?.width ?: 400
                val pref = super.getPreferredSize()
                return Dimension(parentWidth, pref.height)
            }
        }.apply {
            isOpaque = true
            background = claudeThinkingBg
            border = JBUI.Borders.empty(4, 8)

            val wrapper = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(4, 10)
            }

            val header = JBLabel("🧠 Thinking").apply {
                foreground = claudeThinkingFg
                font = font.deriveFont(Font.ITALIC, font.size2D - 1)
            }
            wrapper.add(header, BorderLayout.NORTH)

            val displayContent = if (content.length > 300) content.take(300) + "..." else content
            val textArea = createWrappingTextArea(displayContent, claudeThinkingFg).apply {
                font = UIUtil.getLabelFont().deriveFont(Font.ITALIC)
            }
            wrapper.add(textArea, BorderLayout.CENTER)
            add(wrapper, BorderLayout.CENTER)
        }
    }

    private fun addPanel(panel: JPanel) {
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
        return "ClaudeCodeRenderer[$agentKey](panels=${container.componentCount})"
    }
}

