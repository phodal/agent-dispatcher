package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
 * - Left: Agent selector dropdown
 * - Right: Send/Stop button
 */
class ChatInputToolbar(
    private val project: Project,
    private val onSendClick: () -> Unit,
    private val onStopClick: () -> Unit,
) : JPanel(BorderLayout()) {

    private val agentComboBox = ComboBox<String>()
    private val sendButton = JButton("Send", AllIcons.Actions.Execute)
    private val stopButton = JButton("Stop", AllIcons.Actions.Suspend)
    private val statusLabel = JBLabel()

    private var agentKeys: List<String> = emptyList()
    private var agents: Map<String, AcpAgentConfig> = emptyMap()
    private var isUpdating = false
    private var isProcessing = false

    // Callbacks
    private var onAgentSelect: (String) -> Unit = {}
    private var onConfigureClick: () -> Unit = {}

    private val CONFIGURE_OPTION = "Configure Agents..."

    init {
        border = JBUI.Borders.empty(4, 8)
        isOpaque = false

        // Left side: Agent selector + status
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false

            agentComboBox.preferredSize = Dimension(180, 28)
            agentComboBox.addActionListener {
                if (isUpdating) return@addActionListener
                val selectedItem = agentComboBox.selectedItem as? String
                val selectedIndex = agentComboBox.selectedIndex

                when (selectedItem) {
                    CONFIGURE_OPTION -> onConfigureClick()
                    else -> {
                        if (selectedIndex in agentKeys.indices) {
                            onAgentSelect(agentKeys[selectedIndex])
                        }
                    }
                }
            }
            add(agentComboBox)

            statusLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(statusLabel)
        }
        add(leftPanel, BorderLayout.WEST)

        // Right side: Config + Send/Stop buttons
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false

            // Config button
            val configButton = JButton(AllIcons.General.Settings).apply {
                toolTipText = "Configure ACP Agents"
                preferredSize = Dimension(28, 28)
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { onConfigureClick() }
            }
            add(configButton)

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
        agentComboBox.isEnabled = !processing
    }

    fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
    }

    fun setStatusText(text: String) {
        statusLabel.text = text
    }

    fun setAgents(agentsMap: Map<String, AcpAgentConfig>) {
        agents = agentsMap
        rebuildAgentComboBox()
    }

    fun setCurrentAgent(agentKey: String?) {
        if (agentKey == null) return
        isUpdating = true
        try {
            val index = agentKeys.indexOf(agentKey)
            if (index >= 0) {
                agentComboBox.selectedIndex = index
            }
        } finally {
            isUpdating = false
        }
    }

    fun setOnAgentSelect(callback: (String) -> Unit) {
        onAgentSelect = callback
    }

    fun setOnConfigureClick(callback: () -> Unit) {
        onConfigureClick = callback
    }

    private fun rebuildAgentComboBox() {
        isUpdating = true
        try {
            agentComboBox.removeAllItems()
            val keys = mutableListOf<String>()

            agents.forEach { (key, config) ->
                val displayName = config.description.ifBlank { key }
                agentComboBox.addItem(displayName)
                keys.add(key)
            }

            agentKeys = keys
            agentComboBox.addItem(CONFIGURE_OPTION)
        } finally {
            isUpdating = false
        }
    }
}
