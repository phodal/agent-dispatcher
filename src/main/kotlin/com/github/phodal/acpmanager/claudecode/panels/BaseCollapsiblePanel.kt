package com.github.phodal.acpmanager.claudecode.panels

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Modern base class for collapsible panels.
 *
 * Design principles:
 * - Clean, minimal header with subtle expand indicator
 * - Smooth visual hierarchy
 * - Compact but readable
 */
abstract class BaseCollapsiblePanel(
    protected val headerColor: Color,
    initiallyExpanded: Boolean = false
) : JPanel(), CollapsiblePanel {

    override val component: JPanel get() = this
    override var isExpanded: Boolean = initiallyExpanded
        set(value) {
            field = value
            updateExpandedState()
        }

    protected val headerPanel: JPanel
    protected val headerIcon: JBLabel
    protected val headerTitle: JBLabel
    protected val contentPanel: JPanel

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(2, 8)

        // Header panel - clean layout
        headerPanel = JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // Expand icon - subtle
        headerIcon = JBLabel(getExpandIcon()).apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(10f)
        }
        headerPanel.add(headerIcon, BorderLayout.WEST)

        // Title - normal weight
        headerTitle = JBLabel().apply {
            foreground = headerColor
            font = font.deriveFont(Font.PLAIN, font.size2D)
        }
        headerPanel.add(headerTitle, BorderLayout.CENTER)

        add(headerPanel)

        // Content panel (collapsible)
        contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyLeft(16)
            alignmentX = Component.LEFT_ALIGNMENT
            isVisible = initiallyExpanded
        }
        add(contentPanel)

        // Click to toggle
        headerPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggle()
            }
        })
    }

    protected fun getExpandIcon(): String = if (isExpanded) "▼" else "▶"

    protected open fun updateExpandedState() {
        headerIcon.text = getExpandIcon()
        contentPanel.isVisible = isExpanded

        // Force recalculation of sizes
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

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }

    /**
     * Set the header title text.
     */
    fun setTitle(title: String) {
        headerTitle.text = title
    }

    /**
     * Update the header color.
     */
    fun setHeaderColor(color: Color) {
        headerIcon.foreground = color
        headerTitle.foreground = color
    }
}

