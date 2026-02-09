package com.github.phodal.acpmanager.ui

import com.github.phodal.acpmanager.acp.AcpSessionManager
import com.github.phodal.acpmanager.config.AcpConfigService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Simplified main panel for ACP Manager.
 *
 * UI layout:
 * - Center: Tabbed chat panels for each agent session
 * - Bottom (in each chat panel): Agent selection and config button
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

    init {
        // No toolbar - clean UI
        setContent(tabbedPane)
        tabbedPane.addTab("Welcome", emptyPanel)

        // Load config and auto-detect agents
        configService.reloadConfig()

        // Watch session changes
        startSessionObserver()
    }

    private fun createEmptyPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            // Welcome content in center
            val welcomeContent = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                val gbc = GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.CENTER
                }

                val titleLabel = JBLabel("Welcome to ACP Manager").apply {
                    font = font.deriveFont(Font.BOLD, 18f)
                    foreground = UIUtil.getLabelForeground()
                }
                add(titleLabel, gbc)

                gbc.gridy = 1
                gbc.insets = Insets(12, 0, 0, 0)
                
                val config = configService.loadConfig()
                val agentCount = config.agents.size
                
                val subtitleLabel = JBLabel(
                    if (agentCount > 0) {
                        "Detected $agentCount agent(s) from PATH and config files"
                    } else {
                        "No agents detected"
                    }
                ).apply {
                    foreground = UIUtil.getLabelDisabledForeground()
                }
                add(subtitleLabel, gbc)

                gbc.gridy = 2
                gbc.insets = Insets(24, 0, 0, 0)
                val hintLabel = JBLabel("<html><center>" +
                    "Select an agent below and type your message to start" +
                    "</center></html>").apply {
                    foreground = UIUtil.getLabelDisabledForeground()
                    font = font.deriveFont(font.size2D - 0.5f)
                }
                add(hintLabel, gbc)
            }
            add(welcomeContent, BorderLayout.CENTER)
            
            // Input area at bottom
            val inputArea = JBTextArea(4, 40).apply {
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(8)
                font = UIUtil.getLabelFont().deriveFont(14f)
                emptyText.text = "Select an agent and type your message... (Enter to send)"
            }
            
            inputArea.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                        e.consume()
                        val text = inputArea.text.trim()
                        if (text.isNotEmpty()) {
                            startFirstSession(text)
                            inputArea.text = ""
                        }
                    }
                }
            })
            
            val inputToolbar = ChatInputToolbar(
                project = project,
                onSendClick = {
                    val text = inputArea.text.trim()
                    if (text.isNotEmpty()) {
                        startFirstSession(text)
                        inputArea.text = ""
                    }
                },
                onStopClick = {}
            ).apply {
                val config = configService.loadConfig()
                setAgents(config.agents)
                if (config.activeAgent != null) {
                    setCurrentAgent(config.activeAgent)
                }
                setOnAgentSelect { selectedKey ->
                    // Just update selection, don't connect yet
                }
                setOnConfigureClick { showConfigDialog() }
            }
            
            val inputPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.customLineTop(com.intellij.ui.JBColor.border())
                add(JBScrollPane(inputArea).apply {
                    preferredSize = Dimension(0, JBUI.scale(100))
                    border = JBUI.Borders.empty(4, 8)
                }, BorderLayout.CENTER)
                add(inputToolbar, BorderLayout.SOUTH)
            }
            add(inputPanel, BorderLayout.SOUTH)
        }
    }
    
    private fun startFirstSession(initialMessage: String) {
        val config = configService.loadConfig()
        val agentKey = config.activeAgent ?: config.agents.keys.firstOrNull()
        
        if (agentKey == null) {
            Messages.showWarningDialog(
                project,
                "No agents available. Please configure agents first.",
                "No Agents"
            )
            return
        }
        
        // Connect the agent
        scope.launch(Dispatchers.IO) {
            try {
                sessionManager.connectAgent(agentKey)
                // Wait a bit for the UI to update
                delay(100)
                // Send the initial message
                val session = sessionManager.getSession(agentKey)
                session?.sendMessage(initialMessage)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to start session: ${e.message}",
                        "Connection Error"
                    )
                }
            }
        }
    }

    private fun startSessionObserver() {
        scope.launch {
            sessionManager.sessionKeys.collectLatest { keys ->
                ApplicationManager.getApplication().invokeLater {
                    updateUI(keys)
                }
            }
        }
    }

    private fun updateUI(sessionKeys: List<String>) {
        // Remove tabs for disconnected sessions
        val existingKeys = chatPanels.keys.toList()
        existingKeys.forEach { key ->
            if (!sessionKeys.contains(key)) {
                val panel = chatPanels.remove(key)
                val index = (0 until tabbedPane.tabCount).find {
                    tabbedPane.getComponentAt(it) == panel
                }
                if (index != null && index >= 0) {
                    tabbedPane.removeTabAt(index)
                }
            }
        }

        // Add/update tabs for connected sessions
        sessionKeys.forEach { key ->
            if (!chatPanels.containsKey(key)) {
                // Get or create session
                val session = sessionManager.getOrCreateSession(key)
                
                // Create new chat panel
                val chatPanel = ChatPanel(project, session)
                chatPanels[key] = chatPanel
                
                // Configure the input toolbar
                val config = configService.loadConfig()
                chatPanel.updateInputToolbar(
                    agents = config.agents,
                    currentAgentKey = key,
                    onAgentSelect = { selectedKey ->
                        // Switch to or create session for selected agent
                        scope.launch(Dispatchers.IO) {
                            try {
                                sessionManager.connectAgent(selectedKey)
                            } catch (e: Exception) {
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showErrorDialog(
                                        project,
                                        "Failed to connect to agent: ${e.message}",
                                        "Connection Error"
                                    )
                                }
                            }
                        }
                    },
                    onConfigureClick = { showConfigDialog() }
                )

                // Remove welcome tab if this is the first session
                if (tabbedPane.tabCount == 1 && tabbedPane.getComponentAt(0) == emptyPanel) {
                    tabbedPane.removeTabAt(0)
                }

                // Add new tab
                tabbedPane.addTab(key, chatPanel)
                tabbedPane.selectedComponent = chatPanel
            }
        }

        // Show welcome tab if no sessions
        if (chatPanels.isEmpty() && tabbedPane.componentCount == 0) {
            tabbedPane.addTab("Welcome", emptyPanel)
        }
    }

    private fun showConfigDialog() {
        val config = configService.loadConfig()
        val message = buildString {
            appendLine("Detected ${config.agents.size} ACP Agent(s):")
            appendLine()
            if (config.agents.isEmpty()) {
                appendLine("No agents found.")
                appendLine()
                appendLine("Agents are auto-detected from:")
                appendLine("1. System PATH (common CLIs)")
                appendLine("2. ~/.autodev/config.yaml")
                appendLine("3. ~/.acp-manager/config.yaml")
            } else {
                config.agents.forEach { (key, agent) ->
                    appendLine("• $key: ${agent.command} ${agent.args.joinToString(" ")}")
                }
                appendLine()
                appendLine("Sources:")
                appendLine("• Auto-detected from PATH")
                appendLine("• ~/.autodev/config.yaml")
                appendLine("• ~/.acp-manager/config.yaml")
            }
            appendLine()
            appendLine("To add custom agents, edit:")
            appendLine(configService.getGlobalConfigFile().absolutePath)
        }
        
        Messages.showInfoMessage(
            project,
            message,
            "ACP Agent Configuration"
        )
    }

    override fun dispose() {
        scope.cancel()
        chatPanels.values.forEach { it.dispose() }
        chatPanels.clear()
    }
}
