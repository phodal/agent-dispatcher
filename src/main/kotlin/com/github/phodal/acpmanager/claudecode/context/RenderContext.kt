package com.github.phodal.acpmanager.claudecode.context

import com.github.phodal.acpmanager.claudecode.panels.RenderPanel
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.JPanel

/**
 * Shared context for all render event handlers.
 * Contains common state and utilities needed during rendering.
 */
class RenderContext(
    val contentPanel: JPanel,
    val scrollCallback: () -> Unit,
    val agentKey: String
) {
    // Panel registry for managing active panels
    val panelRegistry = PanelRegistry()

    // Streaming state
    var currentThinkingPanel: RenderPanel? = null
    var currentMessagePanel: RenderPanel? = null
    val thinkingBuffer = StringBuilder()
    val messageBuffer = StringBuilder()
    var currentThinkingSignature: String? = null

    // Color scheme
    val colors = RenderColors()

    /**
     * Add a panel to the content panel.
     * Note: We don't set fixed maximumSize/minimumSize here to allow
     * collapsible panels to dynamically resize when toggled.
     */
    fun addPanel(panel: JPanel) {
        panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        contentPanel.add(panel)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    /**
     * Insert a panel at a specific index.
     * Note: We don't set fixed maximumSize/minimumSize here to allow
     * collapsible panels to dynamically resize when toggled.
     */
    fun insertPanel(panel: JPanel, index: Int) {
        panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        contentPanel.add(panel, index)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    /**
     * Scroll to bottom of content.
     */
    fun scrollToBottom() {
        javax.swing.SwingUtilities.invokeLater {
            scrollCallback()
        }
    }

    /**
     * Clear all state.
     */
    fun clear() {
        contentPanel.removeAll()
        panelRegistry.clear()
        currentThinkingPanel = null
        currentMessagePanel = null
        thinkingBuffer.clear()
        messageBuffer.clear()
        currentThinkingSignature = null
        contentPanel.revalidate()
        contentPanel.repaint()
    }
}

/**
 * Color scheme for rendering.
 * Uses subtle, modern colors that don't distract from content.
 */
class RenderColors {
    val panelBg: Color = UIUtil.getPanelBackground()

    // Subtle gray for thinking - blends with background
    val thinkingFg: Color = UIUtil.getLabelDisabledForeground()

    // Normal text color for assistant messages - no special color
    val messageFg: Color = UIUtil.getLabelForeground()

    // Slightly muted blue for user messages - subtle distinction
    val userFg: Color = JBColor(Color(0x5C6BC0), Color(0x7986CB))

    // Normal text color for tools - status shown via icons only
    val toolFg: Color = UIUtil.getLabelForeground()

    // Normal text color for tasks - status shown via icons only
    val taskFg: Color = UIUtil.getLabelForeground()

    // Status colors - only used for icons, not text
    val successIcon: Color = JBColor(Color(0x4CAF50), Color(0x81C784))
    val errorIcon: Color = JBColor(Color(0xE57373), Color(0xEF5350))
    val pendingIcon: Color = UIUtil.getLabelDisabledForeground()
    val inProgressIcon: Color = JBColor(Color(0x64B5F6), Color(0x90CAF9))

    // Secondary text color for timestamps, metadata
    val secondaryFg: Color = UIUtil.getLabelDisabledForeground()

    // Deprecated - kept for compatibility
    @Deprecated("Use successIcon instead", ReplaceWith("successIcon"))
    val successFg: Color = successIcon
    @Deprecated("Use errorIcon instead", ReplaceWith("errorIcon"))
    val errorFg: Color = errorIcon
}

