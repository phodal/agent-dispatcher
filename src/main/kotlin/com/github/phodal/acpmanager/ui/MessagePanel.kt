package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.acp.ChatMessage
import com.github.phodal.acpmanager.acp.MessageRole
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

/**
 * Panel that renders a single chat message.
 */
class MessagePanel(private val message: ChatMessage) : JPanel(BorderLayout()) {

    init {
        isOpaque = true
        border = JBUI.Borders.empty(4, 8)

        when (message.role) {
            MessageRole.USER -> renderUserMessage()
            MessageRole.ASSISTANT -> renderAssistantMessage()
            MessageRole.TOOL_CALL -> renderToolCallMessage()
            MessageRole.TOOL_RESULT -> renderToolResultMessage()
            MessageRole.THINKING -> renderThinkingMessage()
            MessageRole.INFO -> renderInfoMessage()
            MessageRole.ERROR -> renderErrorMessage()
        }
    }

    private fun renderUserMessage() {
        background = JBColor(Color(0xE3F2FD), Color(0x1A3A5C))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 10)
        }

        val header = createHeader("You", JBColor(Color(0x1565C0), Color(0x64B5F6)))
        wrapper.add(header, BorderLayout.NORTH)

        val textArea = createTextArea(message.content)
        wrapper.add(textArea, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    private fun renderAssistantMessage() {
        background = JBColor(Color(0xF5F5F5), Color(0x2B2B2B))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 10)
        }

        val header = createHeader("Assistant", JBColor(Color(0x2E7D32), Color(0x81C784)))
        wrapper.add(header, BorderLayout.NORTH)

        val textArea = createTextArea(message.content)
        wrapper.add(textArea, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    private fun renderToolCallMessage() {
        background = JBColor(Color(0xFFF3E0), Color(0x3E2723))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 10)
        }

        val kindLabel = message.toolCallKind?.let { " [$it]" } ?: ""
        val statusIcon = when (message.toolCallStatus) {
            com.agentclientprotocol.model.ToolCallStatus.IN_PROGRESS -> "\u25B6" // play
            com.agentclientprotocol.model.ToolCallStatus.PENDING -> "\u25CB"     // circle
            else -> "\u25A0" // square
        }

        val label = JBLabel("$statusIcon Tool: ${message.content}$kindLabel").apply {
            foreground = JBColor(Color(0xE65100), Color(0xFFB74D))
            font = font.deriveFont(Font.BOLD, font.size2D - 1)
        }

        wrapper.add(label, BorderLayout.CENTER)
        add(wrapper, BorderLayout.CENTER)
    }

    private fun renderToolResultMessage() {
        background = JBColor(Color(0xE8F5E9), Color(0x1B3A1B))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 10)
        }

        val isSuccess = message.toolCallStatus == com.agentclientprotocol.model.ToolCallStatus.COMPLETED
        val icon = if (isSuccess) "\u2713" else "\u2717" // checkmark or x

        val content = message.content
        val displayContent = if (content.length > 500) content.take(500) + "..." else content

        val textArea = createTextArea("$icon $displayContent")
        textArea.foreground = if (isSuccess) {
            JBColor(Color(0x2E7D32), Color(0x81C784))
        } else {
            JBColor(Color(0xC62828), Color(0xEF9A9A))
        }
        textArea.font = textArea.font.deriveFont(textArea.font.size2D - 1)

        wrapper.add(textArea, BorderLayout.CENTER)
        add(wrapper, BorderLayout.CENTER)
    }

    private fun renderThinkingMessage() {
        background = JBColor(Color(0xF3E5F5), Color(0x2A1A2E))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 10)
        }

        val header = JBLabel("\uD83D\uDCA1 Thinking").apply {
            foreground = JBColor(Color(0x6A1B9A), Color(0xCE93D8))
            font = font.deriveFont(Font.ITALIC, font.size2D - 1)
        }
        wrapper.add(header, BorderLayout.NORTH)

        val content = message.content
        val displayContent = if (content.length > 300) content.take(300) + "..." else content

        val textArea = createTextArea(displayContent)
        textArea.foreground = JBColor(Color(0x6A1B9A), Color(0xCE93D8))
        textArea.font = textArea.font.deriveFont(Font.ITALIC, textArea.font.size2D - 1)
        wrapper.add(textArea, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    private fun renderInfoMessage() {
        background = JBColor(Color(0xE0F7FA), Color(0x1A2F33))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(3, 10)
        }

        val label = JBLabel("\u2139 ${message.content}").apply {
            foreground = JBColor(Color(0x00695C), Color(0x80CBC4))
            font = font.deriveFont(Font.ITALIC, font.size2D - 1)
        }
        wrapper.add(label, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    private fun renderErrorMessage() {
        background = JBColor(Color(0xFFEBEE), Color(0x3A1A1A))

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 10)
        }

        val label = JBLabel("\u26A0 ${message.content}").apply {
            foreground = JBColor(Color(0xC62828), Color(0xEF9A9A))
            font = font.deriveFont(font.size2D - 1)
        }
        wrapper.add(label, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    private fun createHeader(name: String, color: Color): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)

            val nameLabel = JBLabel(name).apply {
                foreground = color
                font = font.deriveFont(Font.BOLD)
            }
            add(nameLabel, BorderLayout.WEST)

            val timeFormat = SimpleDateFormat("HH:mm:ss")
            val timeLabel = JBLabel(timeFormat.format(Date(message.timestamp))).apply {
                foreground = UIUtil.getLabelDisabledForeground()
                font = font.deriveFont(font.size2D - 2)
            }
            add(timeLabel, BorderLayout.EAST)
        }
    }

    private fun createTextArea(text: String): JTextArea {
        return JTextArea(text).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont()
            border = null
            foreground = UIUtil.getLabelForeground()
        }
    }
}

/**
 * Panel showing a streaming message (in progress).
 */
class StreamingMessagePanel(text: String, isThinking: Boolean = false) : JPanel(BorderLayout()) {
    private val textArea: JTextArea

    init {
        isOpaque = true
        border = JBUI.Borders.empty(4, 8)

        if (isThinking) {
            background = JBColor(Color(0xF3E5F5), Color(0x2A1A2E))
        } else {
            background = JBColor(Color(0xF5F5F5), Color(0x2B2B2B))
        }

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 10)
        }

        val headerText = if (isThinking) "\uD83D\uDCA1 Thinking..." else "Assistant (typing...)"
        val headerColor = if (isThinking) {
            JBColor(Color(0x6A1B9A), Color(0xCE93D8))
        } else {
            JBColor(Color(0x2E7D32), Color(0x81C784))
        }

        val header = JBLabel(headerText).apply {
            foreground = headerColor
            font = font.deriveFont(Font.BOLD or Font.ITALIC)
        }
        wrapper.add(header, BorderLayout.NORTH)

        textArea = JTextArea(text).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont()
            border = JBUI.Borders.emptyTop(4)
            foreground = UIUtil.getLabelForeground()
        }
        wrapper.add(textArea, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    fun updateText(newText: String) {
        textArea.text = newText
        revalidate()
    }
}
