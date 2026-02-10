package com.github.phodal.acpmanager.claudecode.panels

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Modern streaming panel for displaying assistant messages.
 *
 * Design principles:
 * - Clean header with sender name and timestamp
 * - Smooth streaming display
 * - Consistent with MessagePanel styling
 */
class SimpleStreamingPanel(
    private val name: String,
    private val headerColor: Color
) : JPanel(), StreamingPanel {

    override val component: JPanel get() = this

    private val headerLabel: JBLabel
    private val timestampLabel: JBLabel
    private val contentArea: JTextArea
    private val signatureLabel: JBLabel
    private val startTime = System.currentTimeMillis()

    companion object {
        private val dateFormat = SimpleDateFormat("HH:mm")
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(6, 8, 4, 8)

        // Header with name and timestamp
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(18))
            alignmentX = Component.LEFT_ALIGNMENT
        }

        headerLabel = JBLabel(name).apply {
            foreground = headerColor
            font = font.deriveFont(Font.BOLD, font.size2D - 1)
        }
        headerPanel.add(headerLabel, BorderLayout.WEST)

        timestampLabel = JBLabel(dateFormat.format(Date(startTime))).apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 2)
        }
        headerPanel.add(timestampLabel, BorderLayout.EAST)

        add(headerPanel)

        // Content area
        contentArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.emptyTop(4)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(contentArea)

        // Signature label (hidden by default)
        signatureLabel = JBLabel().apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 2)
            border = JBUI.Borders.emptyTop(2)
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = false
        }
        add(signatureLabel)
    }

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }

    override fun updateContent(content: String) {
        contentArea.text = content
        revalidate()
        repaint()
        parent?.revalidate()
    }

    override fun finalize(content: String, signature: String?) {
        contentArea.text = content
        signature?.let {
            signatureLabel.text = "✓ ${it.take(8)}..."
            signatureLabel.isVisible = true
        }
        revalidate()
        repaint()
        parent?.revalidate()
    }
}

