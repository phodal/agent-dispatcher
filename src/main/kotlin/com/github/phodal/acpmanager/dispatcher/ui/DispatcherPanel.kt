package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.config.AcpConfigService
import com.github.phodal.acpmanager.dispatcher.AgentDispatcher
import com.github.phodal.acpmanager.dispatcher.DefaultAgentDispatcher
import com.github.phodal.acpmanager.dispatcher.idea.IdeaAgentExecutor
import com.github.phodal.acpmanager.dispatcher.idea.IdeaPlanGenerator
import com.github.phodal.acpmanager.dispatcher.model.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

private val log = logger<DispatcherPanel>()

/**
 * Main panel for the Multi-Agent Dispatcher ToolWindow tab.
 *
 * Layout (top to bottom):
 * 1. [MasterAgentPanel] — Master Agent selector, thinking area, plan items
 * 2. [TaskListPanel]    — Task list with agent assignment, progress, execute button
 * 3. Input area         — User input field for describing tasks
 * 4. [LogStreamPanel]   — Real-time log stream with task-level filtering
 */
class DispatcherPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val configService = AcpConfigService.getInstance(project)

    // UI components
    private val masterAgentPanel = MasterAgentPanel()
    private val taskListPanel = TaskListPanel()
    private val logStreamPanel = LogStreamPanel()

    // Dispatcher (initialized lazily when first needed)
    private var dispatcher: AgentDispatcher? = null

    init {
        setupUI()
        loadAgents()
    }

    private fun setupUI() {
        // Main content with vertical split
        val mainPanel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(0x0D1117, 0x0D1117)
        }

        // Top section: Master Agent + Tasks (scrollable)
        val topSection = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false

            add(masterAgentPanel)
            add(taskListPanel)
        }

        // Input area
        val inputPanel = createInputPanel()

        // Split pane: top (master + tasks) / bottom (logs)
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = JScrollPane(topSection).apply {
                border = JBUI.Borders.empty()
            }
            bottomComponent = logStreamPanel
            dividerLocation = 400
            resizeWeight = 0.65
            border = JBUI.Borders.empty()
        }

        mainPanel.add(splitPane, BorderLayout.CENTER)
        mainPanel.add(inputPanel, BorderLayout.SOUTH)

        setContent(mainPanel)

        // Wire up callbacks
        masterAgentPanel.onMasterAgentChanged = { agentKey ->
            dispatcher?.setMasterAgent(agentKey)
        }

        taskListPanel.onExecute = {
            executePlan()
        }

        taskListPanel.onTaskAgentChanged = { taskId, newAgent ->
            dispatcher?.updateTaskAgent(taskId, newAgent)
        }

        taskListPanel.onParallelismChanged = { value ->
            dispatcher?.updateMaxParallelism(value)
        }
    }

    private fun createInputPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(0x161B22, 0x161B22)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBColor(0x21262D, 0x21262D)),
                JBUI.Borders.empty(8, 16)
            )

            val inputArea = JBTextArea(2, 40).apply {
                lineWrap = true
                wrapStyleWord = true
                background = JBColor(0x0D1117, 0x0D1117)
                foreground = JBColor(0xC9D1D9, 0xC9D1D9)
                border = JBUI.Borders.compound(
                    BorderFactory.createLineBorder(JBColor(0x30363D, 0x30363D)),
                    JBUI.Borders.empty(8)
                )
                font = Font("SansSerif", Font.PLAIN, 13)

                addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                            e.consume()
                            val text = text.trim()
                            if (text.isNotEmpty()) {
                                startPlanning(text)
                                this@apply.text = ""
                            }
                        }
                    }
                })
            }

            val sendButton = JButton(AllIcons.Actions.Execute).apply {
                toolTipText = "Generate plan and start execution"
                preferredSize = Dimension(36, 36)
                addActionListener {
                    val text = inputArea.text.trim()
                    if (text.isNotEmpty()) {
                        startPlanning(text)
                        inputArea.text = ""
                    }
                }
            }

            val hintLabel = JBLabel("Describe your task. Press Enter to generate plan.").apply {
                foreground = JBColor(0x484F58, 0x484F58)
                font = font.deriveFont(11f)
                border = JBUI.Borders.emptyBottom(4)
            }

            add(hintLabel, BorderLayout.NORTH)
            add(JScrollPane(inputArea).apply {
                border = JBUI.Borders.empty()
                preferredSize = Dimension(0, 60)
            }, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
    }

    private fun loadAgents() {
        scope.launch(Dispatchers.IO) {
            try {
                configService.reloadConfig()
                val config = configService.loadConfig()
                val agentKeys = config.agents.keys.toList()

                withContext(Dispatchers.Main) {
                    if (agentKeys.isEmpty()) {
                        logStreamPanel.appendLog(
                            AgentLogEntry(
                                level = LogLevel.WRN,
                                source = "Master",
                                message = "No ACP agents detected. Configure agents in ~/.acp-manager/config.yaml",
                            )
                        )
                        masterAgentPanel.updateThinking("No agents found. Please configure ACP agents first.")
                        return@withContext
                    }

                    masterAgentPanel.setAvailableAgents(agentKeys)
                    taskListPanel.setAvailableAgents(agentKeys)

                    val defaultAgent = config.activeAgent ?: agentKeys.firstOrNull()
                    if (defaultAgent != null) {
                        masterAgentPanel.setSelectedAgent(defaultAgent)
                    }

                    // Build agent roles from config
                    val roles = agentKeys.map { key ->
                        AgentRole(
                            id = key,
                            name = config.agents[key]?.description?.ifEmpty { key } ?: key,
                            acpAgentKey = key,
                        )
                    }

                    // Initialize dispatcher
                    val ideaExecutor = IdeaAgentExecutor(project)
                    val ideaPlanGenerator = IdeaPlanGenerator(project)
                    val agentDispatcher = DefaultAgentDispatcher(ideaPlanGenerator, ideaExecutor, scope)
                    agentDispatcher.setAgentRoles(roles)
                    if (defaultAgent != null) {
                        agentDispatcher.setMasterAgent(defaultAgent)
                    }
                    dispatcher = agentDispatcher

                    // Observe state changes
                    observeDispatcher(agentDispatcher)

                    logStreamPanel.appendLog(
                        AgentLogEntry(
                            level = LogLevel.INF,
                            source = "Master",
                            message = "Dispatcher ready. ${agentKeys.size} agent(s) available: ${agentKeys.joinToString(", ")}. Master: $defaultAgent",
                        )
                    )
                }
            } catch (e: Exception) {
                log.warn("Failed to initialize dispatcher: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    logStreamPanel.appendLog(
                        AgentLogEntry(
                            level = LogLevel.ERR,
                            source = "Master",
                            message = "Failed to initialize dispatcher: ${e.message}",
                        )
                    )
                    masterAgentPanel.updateStatus(DispatcherStatus.FAILED)
                }
            }
        }
    }

    private fun observeDispatcher(dispatcher: DefaultAgentDispatcher) {
        // Observe state
        scope.launch {
            dispatcher.state.collectLatest { state ->
                updateUI(state)
            }
        }

        // Observe log stream
        scope.launch {
            dispatcher.logStream.collect { logEntry ->
                logStreamPanel.appendLog(logEntry)
            }
        }
    }

    private fun updateUI(state: DispatcherState) {
        masterAgentPanel.updateStatus(state.status)

        // Show final output when available
        masterAgentPanel.updateFinalOutput(state.finalOutput)

        state.plan?.let { plan ->
            masterAgentPanel.updateThinking(plan.thinking)
            masterAgentPanel.updatePlanItems(
                plan.tasks.mapIndexed { index, task ->
                    PlanItemDisplay(
                        index = String.format("%02d", index + 1),
                        text = task.title,
                        status = task.status.name,
                    )
                }
            )
            taskListPanel.updateTasks(plan.tasks)
            taskListPanel.setParallelism(plan.maxParallelism)
            taskListPanel.updateActiveAgents(plan.activeTasks, plan.totalTasks)

            // Disable execute when already running/completed
            taskListPanel.setExecuteEnabled(
                state.status != DispatcherStatus.RUNNING &&
                        state.status != DispatcherStatus.COMPLETED
            )
        }
    }

    private fun startPlanning(userInput: String) {
        val d = dispatcher
        if (d == null) {
            logStreamPanel.appendLog(
                AgentLogEntry(
                    level = LogLevel.ERR,
                    source = "Master",
                    message = "Dispatcher not initialized. No agents configured?",
                )
            )
            return
        }

        logStreamPanel.clearLogs()

        // Immediate UI feedback
        masterAgentPanel.updateStatus(DispatcherStatus.PLANNING)
        masterAgentPanel.updateThinking("Planning: $userInput")
        logStreamPanel.appendLog(
            AgentLogEntry(
                level = LogLevel.INF,
                source = "Master",
                message = "User request: ${userInput.take(100)}...",
            )
        )

        scope.launch {
            try {
                d.startPlanning(userInput)
            } catch (e: Exception) {
                log.warn("Planning failed: ${e.message}", e)
                masterAgentPanel.updateStatus(DispatcherStatus.FAILED)
                masterAgentPanel.updateThinking("Planning failed: ${e.message}")
                logStreamPanel.appendLog(
                    AgentLogEntry(
                        level = LogLevel.ERR,
                        source = "Master",
                        message = "Planning failed: ${e.message}",
                    )
                )
            }
        }
    }

    private fun executePlan() {
        val d = dispatcher ?: return
        logStreamPanel.appendLog(
            AgentLogEntry(
                level = LogLevel.INF,
                source = "Master",
                message = "Starting plan execution...",
            )
        )
        scope.launch {
            try {
                d.executePlan()
            } catch (e: Exception) {
                log.warn("Execution failed: ${e.message}", e)
                logStreamPanel.appendLog(
                    AgentLogEntry(
                        level = LogLevel.ERR,
                        source = "Master",
                        message = "Execution failed: ${e.message}",
                    )
                )
            }
        }
    }

    override fun dispose() {
        dispatcher?.reset()
        scope.cancel()
    }
}
