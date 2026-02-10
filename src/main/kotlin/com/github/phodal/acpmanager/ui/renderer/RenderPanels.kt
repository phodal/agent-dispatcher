package com.github.phodal.acpmanager.ui.renderer

import com.agentclientprotocol.model.ToolCallStatus
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Panel for displaying streaming content (thinking or message).
 */
class StreamingPanel(
    headerText: String,
    headerColor: Color,
    bgColor: Color,
) : JPanel(BorderLayout()) {

    private val textArea: JTextArea

    override fun getPreferredSize(): Dimension {
        val parentWidth = parent?.width ?: 400
        val pref = super.getPreferredSize()
        return Dimension(parentWidth, pref.height)
    }

    init {
        isOpaque = true
        background = bgColor
        border = JBUI.Borders.empty(4, 8)

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 10)
        }

        val header = JBLabel(headerText).apply {
            foreground = headerColor
            font = font.deriveFont(Font.BOLD or Font.ITALIC)
        }
        wrapper.add(header, BorderLayout.NORTH)

        textArea = object : JTextArea() {
            override fun getPreferredSize(): Dimension {
                val parentWidth = this@StreamingPanel.parent?.width ?: 400
                val availableWidth = maxOf(100, parentWidth - 50)

                val fm = getFontMetrics(font)
                val lines = if (availableWidth > 0 && text.isNotEmpty()) {
                    var lineCount = 0
                    for (line in text.split("\n")) {
                        if (line.isEmpty()) {
                            lineCount++
                        } else {
                            val lineWidth = fm.stringWidth(line)
                            lineCount += maxOf(1, (lineWidth + availableWidth - 1) / availableWidth)
                        }
                    }
                    maxOf(1, lineCount)
                } else {
                    1
                }

                val height = lines * fm.height + 4
                return Dimension(availableWidth, height)
            }
        }.apply {
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

    fun updateContent(content: String) {
        textArea.text = content
        revalidate()
        repaint()
    }

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }
}

/**
 * Panel for displaying a tool call with status updates.
 */
class ToolCallPanel(
    private val toolCallId: String,
    initialTitle: String,
) : JPanel(BorderLayout()) {

    private val statusIcon: JBLabel
    private val titleLabel: JBLabel
    private val resultArea: JTextArea
    private var currentTitle: String = initialTitle
    private var isCompleted = false

    init {
        isOpaque = true
        background = JBColor(Color(0xFFF3E0), Color(0x3E2723))
        border = JBUI.Borders.empty(4, 8)

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 10)
        }

        // Header with status icon and title
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
        }

        statusIcon = JBLabel("▶").apply {
            foreground = JBColor(Color(0xE65100), Color(0xFFB74D))
            font = font.deriveFont(Font.BOLD)
        }
        headerPanel.add(statusIcon)

        titleLabel = JBLabel("Tool: $initialTitle").apply {
            foreground = JBColor(Color(0xE65100), Color(0xFFB74D))
            font = font.deriveFont(Font.BOLD, font.size2D - 1)
        }
        headerPanel.add(titleLabel)

        wrapper.add(headerPanel, BorderLayout.NORTH)

        // Result area (hidden initially)
        resultArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1)
            foreground = JBColor(Color(0x2E7D32), Color(0x81C784))
            isVisible = false
            border = JBUI.Borders.emptyTop(4)
        }
        wrapper.add(resultArea, BorderLayout.CENTER)

        add(wrapper, BorderLayout.CENTER)
    }

    fun updateStatus(status: ToolCallStatus, title: String?) {
        if (isCompleted) return

        title?.let { currentTitle = it }
        titleLabel.text = "Tool: $currentTitle"

        statusIcon.text = when (status) {
            ToolCallStatus.IN_PROGRESS -> "▶"
            ToolCallStatus.PENDING -> "○"
            else -> "■"
        }
        revalidate()
        repaint()
    }

    fun complete(status: ToolCallStatus, output: String?) {
        isCompleted = true
        background = if (status == ToolCallStatus.COMPLETED) {
            JBColor(Color(0xE8F5E9), Color(0x1B3A1B))
        } else {
            JBColor(Color(0xFFEBEE), Color(0x3A1A1A))
        }

        val icon = if (status == ToolCallStatus.COMPLETED) "✓" else "✗"
        statusIcon.text = icon
        statusIcon.foreground = if (status == ToolCallStatus.COMPLETED) {
            JBColor(Color(0x2E7D32), Color(0x81C784))
        } else {
            JBColor(Color(0xC62828), Color(0xEF9A9A))
        }

        titleLabel.foreground = statusIcon.foreground

        val resultText = output?.takeIf { it.isNotBlank() }
            ?: if (status == ToolCallStatus.COMPLETED) "Done" else "Failed"
        val displayText = if (resultText.length > 500) resultText.take(500) + "..." else resultText
        resultArea.text = displayText
        resultArea.foreground = statusIcon.foreground
        resultArea.isVisible = true

        revalidate()
        repaint()
    }

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }
}

