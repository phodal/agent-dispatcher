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
 * Modern, clean panel for displaying user or assistant messages.
 *
 * Design principles:
 * - Clear sender identification
 * - Subtle timestamp
 * - Clean content display
 */
class MessagePanel(
    private val name: String?,
    private val content: String,
    private val timestamp: Long,
    private val headerColor: Color
) : JPanel(), RenderPanel {

    override val component: JPanel get() = this

    companion object {
        private val dateFormat = SimpleDateFormat("HH:mm")
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(6, 8, 4, 8)

        // Determine display name - never show "Unknown"
        val displayName = when {
            !name.isNullOrBlank() -> name
            else -> "User" // Default fallback
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(18))

            // Sender name with subtle styling
            add(JBLabel(displayName).apply {
                foreground = headerColor
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D - 1)
            }, BorderLayout.WEST)

            // Timestamp - very subtle
            add(JBLabel(dateFormat.format(Date(timestamp))).apply {
                foreground = UIUtil.getLabelDisabledForeground()
                font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 2)
            }, BorderLayout.EAST)

            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(header)

        // Content area
        val textArea = JTextArea(content).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.emptyTop(4)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(textArea)
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

