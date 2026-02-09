package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Bottom toolbar for the chat input area.
 *
 * Layout:
 * - Left: Agent selector (with status dots) + status label
 * - Right: Send/Stop button
 */
class ChatInputToolbar(
    private val project: Project,
    private val onSendClick: () -> Unit,
    private val onStopClick: () -> Unit,
) : JPanel(BorderLayout()) {

    val agentSelector = AgentSelectorPanel(project)
    private val sendButton = JButton("Send", AllIcons.Actions.Execute)
    private val stopButton = JButton("Stop", AllIcons.Actions.Suspend)
    private val statusLabel = JBLabel()

    private var isProcessing = false

    init {
        border = JBUI.Borders.empty(4, 8)
        isOpaque = false

        // Left side: Agent selector + status
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(agentSelector)
            statusLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(statusLabel)
        }
        add(leftPanel, BorderLayout.WEST)

        // Right side: Send/Stop buttons
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false

            // Send button
            sendButton.apply {
                preferredSize = Dimension(80, 28)
                addActionListener { onSendClick() }
            }
            add(sendButton)

            // Stop button
            stopButton.apply {
                preferredSize = Dimension(80, 28)
                isVisible = false
                addActionListener { onStopClick() }
            }
            add(stopButton)
        }
        add(rightPanel, BorderLayout.EAST)
    }

    fun setProcessing(processing: Boolean) {
        isProcessing = processing
        sendButton.isVisible = !processing
        stopButton.isVisible = processing
        agentSelector.isEnabled = !processing
    }

    fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
    }

    fun setStatusText(text: String) {
        statusLabel.text = text
    }

    fun setAgents(agentsMap: Map<String, AcpAgentConfig>) {
        agentSelector.setAgents(agentsMap)
    }

    fun setCurrentAgent(agentKey: String?) {
        agentSelector.setCurrentAgent(agentKey)
    }

    fun setOnAgentSelect(callback: (String) -> Unit) {
        agentSelector.onAgentSelected = callback
    }

    fun setOnConfigureClick(callback: () -> Unit) {
        agentSelector.onConfigureClick = callback
    }

    /**
     * Get the currently selected agent key from the selector.
     */
    fun getSelectedAgentKey(): String? {
        return agentSelector.getSelectedAgentKey()
    }

    /**
     * Update a specific agent's connection status indicator.
     */
    fun updateAgentStatus(agentKey: String, status: AgentConnectionStatus) {
        agentSelector.updateAgentStatus(agentKey, status)
    }

    /**
     * Refresh all agent connection statuses.
     */
    fun refreshAllStatuses() {
        agentSelector.refreshAllStatuses()
    }
}
