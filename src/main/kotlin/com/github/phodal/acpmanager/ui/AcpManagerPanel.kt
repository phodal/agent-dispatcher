package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.acp.AcpProcessManager
import com.github.phodal.acpmanager.acp.AcpSessionManager
import com.github.phodal.acpmanager.config.AcpConfigService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import javax.swing.*

/**
 * Main panel for the ACP Manager tool window.
 *
 * Provides:
 * - Agent selector (tabs or dropdown for multi-client)
 * - Chat panel for active session
 * - Toolbar for connecting/disconnecting agents
 * - Agent management (add/remove/edit)
 */
class AcpManagerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionManager = AcpSessionManager.getInstance(project)
    private val configService = AcpConfigService.getInstance(project)

    private val tabbedPane = JTabbedPane(JTabbedPane.TOP)
    private val chatPanels = mutableMapOf<String, ChatPanel>()
    private val emptyPanel = createEmptyPanel()

    private val agentSelector: JComboBox<String>
    private val connectButton: JButton
    private val disconnectButton: JButton
    private val statusIndicator: JBLabel

    init {
        // Create toolbar components
        agentSelector = JComboBox<String>().apply {
            preferredSize = Dimension(JBUI.scale(150), preferredSize.height)
            addActionListener { onAgentSelected() }
        }

        connectButton = JButton("Connect").apply {
            addActionListener { onConnect() }
        }

        disconnectButton = JButton("Disconnect").apply {
            isEnabled = false
            addActionListener { onDisconnect() }
        }

        statusIndicator = JBLabel("\u25CF Disconnected").apply {
            foreground = JBColor(Color(0x999999), Color(0x666666))
            font = font.deriveFont(font.size2D - 1)
        }

        // Build top toolbar
        val toolbarPanel = buildToolbarPanel()
        setToolbar(toolbarPanel)

        // Content
        setContent(tabbedPane)
        tabbedPane.addTab("Welcome", emptyPanel)

        // Load config and populate agent selector
        refreshAgentList()

        // Watch session changes
        startSessionObserver()
    }

    private fun buildToolbarPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
        }

        // Left side: agent selector + connect/disconnect
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(JBLabel("Agent:"))
            add(agentSelector)
            add(connectButton)
            add(disconnectButton)
        }

        // Right side: actions
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false

            val addButton = JButton("+").apply {
                toolTipText = "Add Agent"
                addActionListener { onAddAgent() }
            }
            val editButton = JButton("Edit").apply {
                toolTipText = "Edit Agent"
                addActionListener { onEditAgent() }
            }
            val removeButton = JButton("-").apply {
                toolTipText = "Remove Agent"
                addActionListener { onRemoveAgent() }
            }
            val refreshButton = JButton("\u21BB").apply {
                toolTipText = "Refresh Config"
                addActionListener { refreshAgentList() }
            }

            add(statusIndicator)
            add(Box.createHorizontalStrut(8))
            add(addButton)
            add(editButton)
            add(removeButton)
            add(refreshButton)
        }

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(rightPanel, BorderLayout.EAST)

        return panel
    }

    private fun createEmptyPanel(): JPanel {
        return JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.CENTER
            }

            val titleLabel = JBLabel("ACP Manager").apply {
                font = font.deriveFont(Font.BOLD, 18f)
                foreground = UIUtil.getLabelForeground()
            }
            add(titleLabel, gbc)

            gbc.gridy = 1
            gbc.insets = Insets(8, 0, 0, 0)
            val subtitleLabel = JBLabel("Select an agent and click Connect to start").apply {
                foreground = UIUtil.getLabelDisabledForeground()
            }
            add(subtitleLabel, gbc)

            gbc.gridy = 2
            gbc.insets = Insets(16, 0, 0, 0)
            val hintLabel = JBLabel("<html><center>Configure agents in ~/.acp-manager/config.yaml<br>or use the + button to add one.</center></html>").apply {
                foreground = UIUtil.getLabelDisabledForeground()
                font = font.deriveFont(font.size2D - 1)
            }
            add(hintLabel, gbc)

            gbc.gridy = 3
            gbc.insets = Insets(16, 0, 0, 0)
            val openConfigButton = JButton("Open Config File").apply {
                addActionListener { openConfigFile() }
            }
            add(openConfigButton, gbc)
        }
    }

    private fun refreshAgentList() {
        configService.reloadConfig()
        val agents = configService.getAgentKeys()
        val model = DefaultComboBoxModel(agents.toTypedArray())
        agentSelector.model = model

        // Select active agent if configured
        val active = configService.getActiveAgentKey()
        if (active != null && agents.contains(active)) {
            agentSelector.selectedItem = active
        }
    }

    private fun onAgentSelected() {
        val selected = agentSelector.selectedItem as? String ?: return
        val session = sessionManager.getSession(selected)
        if (session != null && session.isConnected) {
            switchToSession(selected)
        }
    }

    private fun onConnect() {
        val agentKey = agentSelector.selectedItem as? String ?: run {
            Messages.showWarningDialog(project, "Please select an agent first.", "No Agent Selected")
            return
        }

        connectButton.isEnabled = false
        statusIndicator.text = "\u25CF Connecting..."
        statusIndicator.foreground = JBColor(Color(0xFFA000), Color(0xFFB300))

        scope.launch(Dispatchers.IO) {
            try {
                sessionManager.connectAgent(agentKey)
                withContext(Dispatchers.Main) {
                    switchToSession(agentKey)
                    connectButton.isEnabled = true
                    disconnectButton.isEnabled = true
                    statusIndicator.text = "\u25CF Connected: $agentKey"
                    statusIndicator.foreground = JBColor(Color(0x4CAF50), Color(0x81C784))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectButton.isEnabled = true
                    statusIndicator.text = "\u25CF Error"
                    statusIndicator.foreground = JBColor(Color(0xF44336), Color(0xEF9A9A))
                    Messages.showErrorDialog(
                        project,
                        "Failed to connect to agent '$agentKey':\n${e.message}",
                        "Connection Error"
                    )
                }
            }
        }
    }

    private fun onDisconnect() {
        val agentKey = sessionManager.activeSessionKey.value ?: return

        scope.launch(Dispatchers.IO) {
            sessionManager.disconnectAgent(agentKey)
            withContext(Dispatchers.Main) {
                disconnectButton.isEnabled = false
                statusIndicator.text = "\u25CF Disconnected"
                statusIndicator.foreground = JBColor(Color(0x999999), Color(0x666666))
            }
        }
    }

    private fun switchToSession(agentKey: String) {
        sessionManager.setActiveSession(agentKey)

        // Check if we already have a tab for this session
        val existingIdx = (0 until tabbedPane.tabCount).firstOrNull {
            tabbedPane.getTitleAt(it) == agentKey
        }

        if (existingIdx != null) {
            tabbedPane.selectedIndex = existingIdx
            return
        }

        // Create new chat panel for this session
        val session = sessionManager.getOrCreateSession(agentKey)
        val chatPanel = ChatPanel(project, session)
        chatPanels[agentKey] = chatPanel

        // Remove welcome tab if it's the only one
        if (tabbedPane.tabCount == 1 && tabbedPane.getTitleAt(0) == "Welcome") {
            tabbedPane.removeTabAt(0)
        }

        // Create tab with close button
        tabbedPane.addTab(agentKey, chatPanel)
        val tabIdx = tabbedPane.tabCount - 1
        tabbedPane.setTabComponentAt(tabIdx, createTabComponent(agentKey))
        tabbedPane.selectedIndex = tabIdx
    }

    private fun createTabComponent(agentKey: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false

            val indicator = JBLabel("\u25CF").apply {
                foreground = JBColor(Color(0x4CAF50), Color(0x81C784))
                font = font.deriveFont(8f)
            }
            add(indicator)

            val label = JBLabel(agentKey)
            add(label)

            val closeButton = JButton("\u2715").apply {
                preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
                font = font.deriveFont(10f)
                isBorderPainted = false
                isContentAreaFilled = false
                toolTipText = "Close session"
                addActionListener { closeSession(agentKey) }
            }
            add(closeButton)
        }
    }

    private fun closeSession(agentKey: String) {
        scope.launch(Dispatchers.IO) {
            sessionManager.removeSession(agentKey)
            withContext(Dispatchers.Main) {
                val tabIdx = (0 until tabbedPane.tabCount).firstOrNull {
                    tabbedPane.getTitleAt(it) == agentKey
                }
                if (tabIdx != null) {
                    tabbedPane.removeTabAt(tabIdx)
                }
                chatPanels.remove(agentKey)?.dispose()

                if (tabbedPane.tabCount == 0) {
                    tabbedPane.addTab("Welcome", emptyPanel)
                }

                disconnectButton.isEnabled = sessionManager.getConnectedSessionKeys().isNotEmpty()
                statusIndicator.text = "\u25CF Disconnected"
                statusIndicator.foreground = JBColor(Color(0x999999), Color(0x666666))
            }
        }
    }

    private fun onAddAgent() {
        val dialog = AgentConfigDialog(project)
        if (dialog.showAndGet()) {
            val key = dialog.getAgentKey()
            val config = dialog.getAgentConfig()
            configService.addOrUpdateAgent(key, config)
            refreshAgentList()
            agentSelector.selectedItem = key
        }
    }

    private fun onEditAgent() {
        val agentKey = agentSelector.selectedItem as? String ?: return
        val config = configService.getAgentConfig(agentKey) ?: return

        val dialog = AgentConfigDialog(project, agentKey, config)
        if (dialog.showAndGet()) {
            configService.addOrUpdateAgent(agentKey, dialog.getAgentConfig())
            refreshAgentList()
        }
    }

    private fun onRemoveAgent() {
        val agentKey = agentSelector.selectedItem as? String ?: return

        val result = Messages.showYesNoDialog(
            project,
            "Remove agent '$agentKey' from configuration?",
            "Remove Agent",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            scope.launch(Dispatchers.IO) {
                sessionManager.removeSession(agentKey)
                configService.removeAgent(agentKey)
                withContext(Dispatchers.Main) {
                    refreshAgentList()
                }
            }
        }
    }

    private fun openConfigFile() {
        val configFile = configService.getGlobalConfigFile()
        if (configFile.exists()) {
            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(configFile.absolutePath)
            if (virtualFile != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    .openFile(virtualFile, true)
            }
        }
    }

    private fun startSessionObserver() {
        scope.launch {
            sessionManager.activeSessionKey.collectLatest { key ->
                ApplicationManager.getApplication().invokeLater {
                    if (key != null) {
                        val session = sessionManager.getSession(key)
                        val connected = session?.isConnected == true
                        disconnectButton.isEnabled = connected
                        if (connected) {
                            statusIndicator.text = "\u25CF Connected: $key"
                            statusIndicator.foreground = JBColor(Color(0x4CAF50), Color(0x81C784))
                        }
                    }
                }
            }
        }
    }

    override fun dispose() {
        chatPanels.values.forEach { it.dispose() }
        chatPanels.clear()
        scope.cancel()
    }
}
