package com.github.phodal.acpmanager.claudecode.panels

import com.agentclientprotocol.model.ToolCallStatus
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Modern, minimal panel for displaying tool call information.
 *
 * Design principles:
 * - Clean, single-line collapsed view with status icon
 * - When expanded, shows content directly (no nested collapsible sections)
 * - Status indicated by icon only, not color
 * - Subtle, non-distracting appearance
 */
class ToolCallPanel(
    private val toolName: String,
    private var title: String,
    accentColor: Color
) : BaseCollapsiblePanel(accentColor, initiallyExpanded = false) {

    private val statusIcon: JBLabel
    private var currentStatus: ToolCallStatus = ToolCallStatus.PENDING
    private var isCompleted = false

    // Direct content display (no nested sections)
    private val contentArea: JTextArea
    private var inputContent: String = ""
    private var outputContent: String = ""

    // Summary label for compact display when collapsed
    private val summaryLabel: JBLabel

    init {
        // Compact border
        border = JBUI.Borders.empty(2, 8)

        // Status icon - simple and clean
        statusIcon = JBLabel("○").apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(12f)
        }

        // Reorganize header: [status] [expand] [title] ... [summary]
        headerPanel.remove(headerIcon)
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(statusIcon)
            add(headerIcon)
        }
        headerPanel.add(leftPanel, BorderLayout.WEST)

        // Title - normal color, no emoji
        headerTitle.text = title
        headerTitle.foreground = UIUtil.getLabelForeground()
        headerTitle.font = headerTitle.font.deriveFont(Font.PLAIN)

        // Summary label for collapsed state
        summaryLabel = JBLabel().apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 1)
            isVisible = false
        }
        headerPanel.add(summaryLabel, BorderLayout.EAST)

        // Content area - direct display, no nested sections
        contentArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1)
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.empty(4, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(contentArea)
    }

    /**
     * Update the tool call status.
     */
    fun updateStatus(status: ToolCallStatus, newTitle: String? = null) {
        if (isCompleted) return
        currentStatus = status
        newTitle?.let {
            title = it
            headerTitle.text = it
        }
        updateStatusIcon()
        revalidate()
        repaint()
    }

    private fun updateStatusIcon() {
        val (icon, color) = when (currentStatus) {
            ToolCallStatus.IN_PROGRESS -> "▶" to getInProgressColor()
            ToolCallStatus.PENDING -> "○" to UIUtil.getLabelDisabledForeground()
            ToolCallStatus.COMPLETED -> "✓" to getSuccessColor()
            ToolCallStatus.FAILED -> "✗" to getErrorColor()
        }
        statusIcon.text = icon
        statusIcon.foreground = color
    }

    private fun getSuccessColor(): Color = com.intellij.ui.JBColor(Color(0x4CAF50), Color(0x81C784))
    private fun getErrorColor(): Color = com.intellij.ui.JBColor(Color(0xE57373), Color(0xEF5350))
    private fun getInProgressColor(): Color = com.intellij.ui.JBColor(Color(0x64B5F6), Color(0x90CAF9))

    /**
     * Update the input parameters.
     */
    fun updateParameters(params: String) {
        if (isCompleted) return
        inputContent = params
        updateContentArea()
        revalidate()
        repaint()
        parent?.revalidate()
    }

    private fun updateContentArea() {
        val content = buildString {
            if (inputContent.isNotEmpty()) {
                append(inputContent)
            }
            if (outputContent.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(outputContent)
            }
        }
        contentArea.text = content
        contentArea.isVisible = content.isNotEmpty()
    }

    /**
     * Complete the tool call with final status and output.
     */
    fun complete(status: ToolCallStatus, output: String?) {
        isCompleted = true
        currentStatus = status
        updateStatusIcon()

        // Store output and update display
        outputContent = output ?: ""
        updateContentArea()

        // Show brief summary in header when collapsed
        if (outputContent.isNotEmpty()) {
            val summary = outputContent.take(50).replace("\n", " ").trim()
            summaryLabel.text = if (outputContent.length > 50) "$summary..." else summary
        } else {
            summaryLabel.text = if (status == ToolCallStatus.COMPLETED) "Done" else "Failed"
        }
        summaryLabel.isVisible = !isExpanded

        revalidate()
        repaint()
        parent?.revalidate()
    }

    override fun updateExpandedState() {
        super.updateExpandedState()
        // Hide summary when expanded, show when collapsed
        summaryLabel.isVisible = !isExpanded && isCompleted
    }

    /**
     * Get the current status.
     */
    fun getStatus(): ToolCallStatus = currentStatus
}

