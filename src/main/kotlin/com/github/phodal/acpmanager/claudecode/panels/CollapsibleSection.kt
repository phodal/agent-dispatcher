package com.github.phodal.acpmanager.claudecode.panels

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Modern collapsible section for nested content.
 *
 * Design principles:
 * - Minimal header with subtle expand indicator
 * - Clean content display
 * - Compact sizing
 */
class CollapsibleSection(
    private val sectionTitle: String,
    private val titleColor: Color
) : JPanel() {

    private var expanded = false
    private val headerLabel: JBLabel
    private val contentArea: JTextArea
    private val scrollPane: JBScrollPane
    private var currentContent: String = ""

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(2, 0)
        alignmentX = Component.LEFT_ALIGNMENT

        // Header - simple and clean
        headerLabel = JBLabel(getHeaderText()).apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 1)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(headerLabel)

        // Content area
        contentArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont().deriveFont(font.size2D - 1)
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.empty(4, 0)
        }

        scrollPane = JBScrollPane(contentArea).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
            preferredSize = Dimension(0, JBUI.scale(60))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(100))
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = false
        }
        add(scrollPane)

        // Click to toggle
        headerLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggle()
            }
        })
    }

    private fun getHeaderText(): String {
        val icon = if (expanded) "▼" else "▶"
        return "$icon $sectionTitle"
    }

    private fun toggle() {
        expanded = !expanded
        headerLabel.text = getHeaderText()
        scrollPane.isVisible = expanded

        invalidate()
        revalidate()
        repaint()

        var p = parent
        while (p != null) {
            p.invalidate()
            p.revalidate()
            p.repaint()
            p = p.parent
        }
    }

    /**
     * Set the content of this section.
     */
    fun setContent(content: String) {
        currentContent = content
        contentArea.text = content
        revalidate()
        repaint()
    }

    /**
     * Get the current content.
     */
    fun getContent(): String = currentContent

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }
}

