package com.github.phodal.acpmanager.claudecode.panels

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Simple streaming panel for displaying streaming content (thinking, messages).
 * Shows content as it streams in, with optional signature verification.
 */
class SimpleStreamingPanel(
    private val name: String,
    private val headerColor: Color
) : JPanel(), StreamingPanel {

    override val component: JPanel get() = this

    private val headerLabel: JBLabel
    private val contentArea: JTextArea
    private val signatureLabel: JBLabel

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(4, 8)

        headerLabel = JBLabel("$name (streaming...)").apply {
            foreground = headerColor
            font = font.deriveFont(Font.BOLD)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(headerLabel)

        contentArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.emptyTop(2)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(contentArea)

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
        headerLabel.text = name
        contentArea.text = content
        signature?.let {
            signatureLabel.text = "✓ Verified (${it.take(8)}...)"
            signatureLabel.isVisible = true
        }
        revalidate()
        repaint()
        parent?.revalidate()
    }
}

