package com.github.phodal.acpmanager.claudecode.panels

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.JTextArea

/**
 * Modern, minimal panel for displaying thinking/reasoning content.
 *
 * Design principles:
 * - Collapsed by default with subtle appearance
 * - No duplicate text - preview only shown when collapsed
 * - Clean, non-distracting gray styling
 */
class CollapsibleThinkingPanel(
    accentColor: Color
) : BaseCollapsiblePanel(accentColor, initiallyExpanded = false), StreamingPanel {

    private val contentArea: JTextArea
    private val signatureLabel: JBLabel
    private var fullContent: String = ""

    init {
        // Subtle border
        border = JBUI.Borders.empty(2, 8)

        // Simple header - just "Thinking" with subtle styling
        headerTitle.text = "Thinking..."
        headerTitle.font = headerTitle.font.deriveFont(Font.ITALIC, headerTitle.font.size2D - 1)
        headerTitle.foreground = UIUtil.getLabelDisabledForeground()

        // Make header icon subtle
        headerIcon.foreground = UIUtil.getLabelDisabledForeground()

        // Content area for full thinking text
        contentArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1)
            foreground = UIUtil.getLabelDisabledForeground()
            border = JBUI.Borders.emptyTop(4)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(contentArea)

        // Signature label (hidden by default)
        signatureLabel = JBLabel().apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 2)
            border = JBUI.Borders.emptyTop(2)
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = false
        }
        contentPanel.add(signatureLabel)
    }

    override fun updateContent(content: String) {
        fullContent = content
        contentArea.text = content
        updateHeaderText()
        revalidate()
        repaint()
        parent?.revalidate()
    }

    override fun finalize(content: String, signature: String?) {
        fullContent = content
        contentArea.text = content

        // Update header to show it's complete
        headerTitle.text = "Thinking"
        headerTitle.font = headerTitle.font.deriveFont(Font.PLAIN)

        signature?.let {
            signatureLabel.text = "✓ ${it.take(8)}..."
            signatureLabel.isVisible = true
        }

        revalidate()
        repaint()
        parent?.revalidate()
    }

    private fun updateHeaderText() {
        // Show brief preview in header only when collapsed and has content
        if (!isExpanded && fullContent.isNotEmpty()) {
            val preview = fullContent.take(40).replace("\n", " ").trim()
            val suffix = if (fullContent.length > 40) "..." else ""
            headerTitle.text = "Thinking: $preview$suffix"
        } else if (isExpanded) {
            headerTitle.text = "Thinking"
        } else {
            headerTitle.text = "Thinking..."
        }
    }

    override fun updateExpandedState() {
        super.updateExpandedState()
        updateHeaderText()
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

