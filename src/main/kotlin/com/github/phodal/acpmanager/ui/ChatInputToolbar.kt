package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Bottom toolbar for the chat input area.
 *
 * Layout:
 * - Left: Agent selector (with status dots) + @ button + / button + status label
 * - Right: Refresh status button + New session button + Send/Stop button
 */
class ChatInputToolbar(
    private val project: Project,
    private val onSendClick: () -> Unit,
    private val onStopClick: () -> Unit,
) : JPanel(BorderLayout()) {

    val agentSelector = AgentSelectorPanel(project)
    private val sendButton = JButton("Send", AllIcons.Actions.Execute)
    private val stopButton = JButton("Stop", AllIcons.Actions.Suspend)
    private val newSessionButton = JButton(AllIcons.Actions.Restart)
    private val refreshStatusButton = JButton(AllIcons.Actions.Refresh)
    private val statusLabel = JBLabel()

    // Completion trigger buttons
    private val mentionButton = createCompletionButton("@", "Add file/symbol mention (Ctrl+@)")
    private val commandButton = createCompletionButton("/", "Insert slash command")

    var onMentionClick: (() -> Unit)? = null
        set(value) {
            field = value
            // Show/hide buttons based on whether callbacks are set
            mentionButton.isVisible = value != null
        }
    var onCommandClick: (() -> Unit)? = null
        set(value) {
            field = value
            // Show/hide buttons based on whether callbacks are set
            commandButton.isVisible = value != null
        }
    var onNewSessionClick: (() -> Unit)? = null

    private var isProcessing = false

    init {
        border = JBUI.Borders.empty(4, 8)
        isOpaque = false

        // Left side: Agent selector + completion buttons + status
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(agentSelector)

            // Add @ button
            mentionButton.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    println("ChatInputToolbar: @ button clicked")
                    onMentionClick?.invoke()
                }

                override fun mouseEntered(e: MouseEvent) {
                    mentionButton.foreground = JBUI.CurrentTheme.Link.Foreground.HOVERED
                }

                override fun mouseExited(e: MouseEvent) {
                    mentionButton.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                }
            })
            add(mentionButton)

            // Add / button
            commandButton.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    println("ChatInputToolbar: / button clicked")
                    onCommandClick?.invoke()
                }

                override fun mouseEntered(e: MouseEvent) {
                    commandButton.foreground = JBUI.CurrentTheme.Link.Foreground.HOVERED
                }

                override fun mouseExited(e: MouseEvent) {
                    commandButton.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                }
            })
            add(commandButton)

            statusLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(statusLabel)
        }
        add(leftPanel, BorderLayout.WEST)

        // Right side: Refresh Status + New Session + Send/Stop buttons
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false

            // Refresh Status button
            refreshStatusButton.apply {
                toolTipText = "Refresh agent connection status"
                preferredSize = Dimension(28, 28)
                isContentAreaFilled = false
                isBorderPainted = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener { 
                    agentSelector.refreshAllStatuses()
                }
            }
            add(refreshStatusButton)

            // New Session button
            newSessionButton.apply {
                toolTipText = "Start new session (clear history and reconnect)"
                preferredSize = Dimension(28, 28)
                isContentAreaFilled = false
                isBorderPainted = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener { onNewSessionClick?.invoke() }
            }
            add(newSessionButton)

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

    private fun createCompletionButton(text: String, tooltip: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = tooltip
            border = JBUI.Borders.empty(2, 8)
        }
    }

    fun setProcessing(processing: Boolean) {
        isProcessing = processing
        sendButton.isVisible = !processing
        stopButton.isVisible = processing
        agentSelector.isEnabled = !processing
        newSessionButton.isEnabled = !processing
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
