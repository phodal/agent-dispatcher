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
 */
class RenderColors {
    val panelBg: Color = UIUtil.getPanelBackground()
    val thinkingFg: Color = UIUtil.getLabelDisabledForeground()
    val messageFg: Color = JBColor(Color(0x2E7D32), Color(0x81C784)) // Green for assistant
    val userFg: Color = JBColor(Color(0x1565C0), Color(0x64B5F6)) // Blue for user
    val toolFg: Color = JBColor(Color(0xE65100), Color(0xFFB74D)) // Orange for tools
    val taskFg: Color = JBColor(Color(0x00695C), Color(0x4DB6AC)) // Teal for tasks
    val successFg: Color = JBColor(Color(0x2E7D32), Color(0x81C784)) // Green for success
    val errorFg: Color = JBColor.RED
}

