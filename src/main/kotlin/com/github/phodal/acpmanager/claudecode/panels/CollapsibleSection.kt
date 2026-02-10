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
 * A collapsible section within a panel.
 * Used for showing input/output sections that can be expanded.
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
        border = JBUI.Borders.empty(1, 0) // Reduced padding
        alignmentX = Component.LEFT_ALIGNMENT

        // Header
        headerLabel = JBLabel(getHeaderText()).apply {
            foreground = titleColor
            font = font.deriveFont(font.size2D - 1)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(headerLabel)

        // Content area with scroll
        contentArea = JTextArea().apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = UIUtil.getLabelFont().deriveFont(font.size2D - 1)
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.empty(2) // Reduced padding
        }

        scrollPane = JBScrollPane(contentArea).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
            preferredSize = Dimension(0, JBUI.scale(80)) // Reduced height
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(120)) // Reduced max height
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
        val preview = if (!expanded && currentContent.isNotEmpty()) {
            " - ${currentContent.take(50)}${if (currentContent.length > 50) "..." else ""}"
        } else ""
        return "$icon $sectionTitle$preview"
    }

    private fun toggle() {
        expanded = !expanded
        headerLabel.text = getHeaderText()
        scrollPane.isVisible = expanded

        // Force recalculation of sizes - critical for proper collapse behavior
        invalidate()
        revalidate()
        repaint()

        // Propagate layout changes up the hierarchy
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
        headerLabel.text = getHeaderText()
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

