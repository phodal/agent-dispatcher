package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.config.AcpConfigService
import com.github.phodal.acpmanager.dispatcher.routa.IdeaRoutaService
import com.github.phodal.acpmanager.services.CoroutineScopeHolder
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.phodal.routa.core.coordinator.CoordinationPhase
import com.phodal.routa.core.runner.OrchestratorPhase
import com.phodal.routa.core.runner.OrchestratorResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private val log = logger<DispatcherPanel>()

/**
 * Main panel for the Multi-Agent Dispatcher ToolWindow tab.
 *
 * Redesigned with DAG + multi-agent architecture:
 *
 * ```
 * ┌─────────────────────────────────┐
 * │  ROUTA (Coordinator)            │  ← Plans tasks, streaming output
 * │         ↓                       │
 * ├─────────────────────────────────┤
 * │  CRAFTERs (Implementors)       │  ← Tabbed, model config, main focus
 * │  [Tab1] [Tab2] [Tab3]          │
 * │  Task info + streaming output   │
 * │         ↓                       │
 * ├─────────────────────────────────┤
 * │  GATE (Verifier)               │  ← Verdict, streaming verification
 * ├─────────────────────────────────┤
 * │  [Input area]            [Send] │  ← User request input
 * └─────────────────────────────────┘
 * ```
 *
 * The flow follows the Routa multi-agent DAG:
 * ROUTA plans → CRAFTERs execute (parallel) → GATE verifies
 */
class DispatcherPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val scopeHolder = CoroutineScopeHolder.getInstance(project)
    private val scope = scopeHolder.createScope("DispatcherPanel")
    private val configService = AcpConfigService.getInstance(project)
    private val routaService = IdeaRoutaService.getInstance(project)

    // ── UI Sections ─────────────────────────────────────────────────────

    private val routaSection = RoutaSectionPanel()
    private val crafterSection = CrafterSectionPanel()
    private val gateSection = GateSectionPanel()

    // Status hint label for displaying MCP server info
    private val hintLabel = JBLabel("Routa DAG: Plan → Execute → Verify").apply {
        foreground = JBColor(0x484F58, 0x484F58)
        font = font.deriveFont(9f)
        border = JBUI.Borders.emptyTop(2)
    }

    // MCP Server status components
    private val mcpStatusLabel = JBLabel("●").apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(10f)
        toolTipText = "MCP Server Status"
    }

    private val mcpUrlLabel = JBLabel("MCP Server: not running").apply {
        foreground = JBColor(0x589DF6, 0x589DF6) // Blue color for link
        font = font.deriveFont(9f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private val mcpTransportLabel = JBLabel("").apply {
        foreground = JBColor(0x8B949E, 0x8B949E) // Gray color for transport type
        font = font.deriveFont(8f)
    }

    private val mcpRefreshButton = JButton(AllIcons.Actions.Refresh).apply {
        isContentAreaFilled = false
        isBorderPainted = false
        preferredSize = Dimension(16, 16)
        toolTipText = "Check MCP Server Status"
    }

    private var currentMcpUrl: String? = null

    // Agent selector components (for unified input panel)
    private val agentLabel = JBLabel("Select Agent").apply {
        foreground = JBColor(0x8B949E, 0x8B949E)
        font = font.deriveFont(11f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Click to change agent"
    }

    private val agentPopup = JPopupMenu().apply {
        background = JBColor(0x161B22, 0x161B22)
        border = BorderFactory.createLineBorder(JBColor(0x30363D, 0x30363D))
    }

    init {
        setupUI()
        loadAgents()
        observeRoutaService()
    }

    // ── UI Setup ────────────────────────────────────────────────────────

    private fun setupUI() {
        val mainPanel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(0x0D1117, 0x0D1117)
        }

        // Create resizable DAG sections using nested JSplitPanes
        // ROUTA + CRAFTERs split
        val routaCrafterSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = routaSection
            bottomComponent = crafterSection
            dividerLocation = 120  // ROUTA starts compact
            resizeWeight = 0.2     // ROUTA gets 20% of extra space
            border = JBUI.Borders.empty()
            dividerSize = 4
            isContinuousLayout = true
        }

        // (ROUTA + CRAFTERs) + GATE split
        val dagSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = routaCrafterSplit
            bottomComponent = gateSection
            dividerLocation = 400  // GATE starts small at bottom
            resizeWeight = 0.85    // Top section gets 85% of extra space, GATE stays small
            border = JBUI.Borders.empty()
            dividerSize = 4
            isContinuousLayout = true
        }

        // Input area at the bottom
        val inputPanel = createInputPanel()

        mainPanel.add(dagSplitPane, BorderLayout.CENTER)
        mainPanel.add(inputPanel, BorderLayout.SOUTH)

        setContent(mainPanel)

        // Wire up CRAFTER model change (ACP agent selection)
        crafterSection.onModelChanged = { model ->
            routaService.crafterModelKey.value = model

            // Update ROUTA section status text to reflect new CRAFTER
            // Since we now use ACP for ROUTA, show the ACP agent info
            val routaInfo = if (routaService.useAcpForRouta.value) {
                "ROUTA: $model (ACP Agent)"
            } else {
                routaService.getActiveLlmConfig()?.let { config ->
                    "ROUTA: ${config.provider}/${config.model} (KoogAgent)"
                } ?: "ROUTA: fallback to ACP"
            }
            routaSection.setPlanningText("✓ Ready. CRAFTER: $model | $routaInfo")
        }

        // Wire up CRAFTER stop callback
        crafterSection.onStopCrafter = { agentId ->
            log.info("Stopping CRAFTER agent: $agentId")
            routaService.stopCrafter(agentId)
        }

        // Wire up ROUTA model change (LLM model selection for KoogAgent, or ACP agent selection)
        routaSection.onModelChanged = { modelName ->
            if (routaService.useAcpForRouta.value) {
                // When using ACP for ROUTA, the model selector changes the ACP agent
                routaService.routaModelKey.value = modelName
                log.info("ROUTA ACP agent changed to: $modelName")

                val crafterModel = routaService.crafterModelKey.value
                routaSection.setPlanningText("✓ Ready. CRAFTER: $crafterModel | ROUTA: $modelName (ACP Agent)")
            } else {
                // Legacy: use LLM configs for KoogAgent
                val configs = routaService.getAvailableLlmConfigs()
                val selected = configs.find { it.name == modelName }
                if (selected != null) {
                    routaService.setLlmModelConfig(selected)
                    log.info("ROUTA LLM model changed to: ${selected.provider}/${selected.model}")

                    val crafterModel = routaService.crafterModelKey.value
                    val llmInfo = "ROUTA: ${selected.provider}/${selected.model} (KoogAgent)"
                    routaSection.setPlanningText("✓ Ready. CRAFTER: $crafterModel | $llmInfo")
                }
            }
        }
    }

    private fun createInputPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(0x161B22, 0x161B22)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBColor(0x21262D, 0x21262D)),
                JBUI.Borders.empty(8, 12)
            )
        }

        // ── Unified input container (Agent + Input + Execute as one block) ──
        val unifiedContainer = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = JBColor(0x0D1117, 0x0D1117)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(0x30363D, 0x30363D), 1, true),
                JBUI.Borders.empty(0)
            )
        }

        // ── Top row: Agent selector (looks like plain text with settings icon) ──
        val settingsIcon = JBLabel(AllIcons.General.Settings).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Select Agent"
            border = JBUI.Borders.empty(0, 8, 0, 4)
        }

        val agentRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 4, 2, 4)
            add(settingsIcon)
            add(agentLabel)
        }

        // Click handlers for agent selection
        val showAgentPopup = { e: MouseEvent ->
            agentPopup.show(e.component, 0, e.component.height)
        }
        settingsIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showAgentPopup(e)
        })
        agentLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showAgentPopup(e)
        })

        // ── Center: Input text area ──
        val inputArea = JBTextArea(3, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            background = JBColor(0x0D1117, 0x0D1117)
            foreground = JBColor(0xC9D1D9, 0xC9D1D9)
            border = JBUI.Borders.empty(4, 12, 4, 12)
            font = Font("SansSerif", Font.PLAIN, 13)
        }
        inputArea.toolTipText = "Describe your task... (Enter to send, Shift+Enter for newline)"

        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    val text = inputArea.text.trim()
                    if (text.isNotEmpty()) {
                        startExecution(text)
                        inputArea.text = ""
                    }
                }
            }
        })

        val inputScroll = JScrollPane(inputArea).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        // ── Bottom row: Execute/Stop button (right-aligned) ──
        val sendButton = JButton(AllIcons.Actions.Execute).apply {
            toolTipText = "Execute (Enter)"
            preferredSize = Dimension(32, 28)
            isContentAreaFilled = false
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val stopButton = JButton(AllIcons.Actions.Suspend).apply {
            toolTipText = "Stop all agents"
            preferredSize = Dimension(32, 28)
            isContentAreaFilled = false
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isVisible = false
        }

        sendButton.addActionListener {
            val text = inputArea.text.trim()
            if (text.isNotEmpty()) {
                startExecution(text)
                inputArea.text = ""
            }
        }

        stopButton.addActionListener {
            stopExecution()
        }

        // Observe isRunning to toggle send/stop buttons
        scope.launch {
            routaService.isRunning.collectLatest { isRunning ->
                withContext(Dispatchers.EDT) {
                    sendButton.isVisible = !isRunning
                    stopButton.isVisible = isRunning
                    inputArea.isEnabled = !isRunning
                }
            }
        }

        val buttonRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 4, 6, 8)
            add(sendButton)
            add(stopButton)
        }

        // Assemble unified container
        unifiedContainer.add(agentRow, BorderLayout.NORTH)
        unifiedContainer.add(inputScroll, BorderLayout.CENTER)
        unifiedContainer.add(buttonRow, BorderLayout.SOUTH)

        // ── Bottom status panel (hint + MCP status) ──
        val bottomPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(6)
        }

        bottomPanel.add(hintLabel, BorderLayout.WEST)

        val mcpStatusPanel = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(mcpStatusLabel)
            add(Box.createHorizontalStrut(4))
            add(mcpUrlLabel)
            add(Box.createHorizontalStrut(6))
            add(mcpTransportLabel)
            add(Box.createHorizontalStrut(4))
            add(mcpRefreshButton)
        }
        bottomPanel.add(mcpStatusPanel, BorderLayout.EAST)

        // Setup MCP URL click listener
        mcpUrlLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                currentMcpUrl?.let { url ->
                    openUrlInBrowser(url)
                }
            }
        })

        // Setup refresh button listener
        mcpRefreshButton.addActionListener {
            checkMcpServerStatus()
        }

        panel.add(unifiedContainer, BorderLayout.CENTER)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * Update the agent popup menu with available agents.
     * Called after agents are loaded.
     */
    private fun updateAgentPopup(agents: List<String>, selectedAgent: String?) {
        agentPopup.removeAll()
        val agentCombo = routaSection.getModelCombo()

        for (agent in agents) {
            val menuItem = JMenuItem(agent).apply {
                background = JBColor(0x161B22, 0x161B22)
                foreground = JBColor(0xC9D1D9, 0xC9D1D9)
                addActionListener {
                    agentCombo.selectedItem = agent
                    agentLabel.text = agent
                }
            }
            agentPopup.add(menuItem)
        }

        // Update the label with selected agent
        if (selectedAgent != null) {
            agentLabel.text = selectedAgent
        } else if (agents.isNotEmpty()) {
            agentLabel.text = agents.first()
        }
    }

    // ── Agent Loading ───────────────────────────────────────────────────

    private fun loadAgents() {
        scope.launch(Dispatchers.IO) {
            try {
                log.info("Loading ACP agents...")
                configService.reloadConfig()
                val config = configService.loadConfig()
                val agentKeys = config.agents.keys.toList()

                // When using ACP for ROUTA (default), we use ACP agents for ROUTA selector
                val useAcpForRouta = routaService.useAcpForRouta.value

                withContext(Dispatchers.EDT) {
                    if (agentKeys.isEmpty()) {
                        val msg = "⚠️ No ACP agents detected. Configure agents in ~/.acp-manager/config.yaml"
                        log.warn(msg)
                        routaSection.setPlanningText(msg)
                        return@withContext
                    }

                    // Set available ACP agents for both ROUTA and CRAFTER selectors
                    if (useAcpForRouta) {
                        // ROUTA uses ACP agents - populate ROUTA selector with ACP agents
                        routaSection.setAvailableModels(agentKeys)
                        log.info("ROUTA: Using ACP Agent mode. ${agentKeys.size} agent(s) available.")
                    } else {
                        // Legacy mode: ROUTA uses LLM configs (KoogAgent)
                        val llmConfigs = routaService.getAvailableLlmConfigs()
                        val activeLlmConfig = routaService.getActiveLlmConfig()
                        if (llmConfigs.isNotEmpty()) {
                            val modelNames = llmConfigs.map { it.name.ifBlank { "${it.provider}/${it.model}" } }
                            routaSection.setAvailableModels(modelNames)
                            if (activeLlmConfig != null) {
                                val activeName = activeLlmConfig.name.ifBlank { "${activeLlmConfig.provider}/${activeLlmConfig.model}" }
                                routaSection.setSelectedModel(activeName)
                            }
                            log.info("ROUTA LLM: ${llmConfigs.size} model(s) available")
                        } else {
                            log.warn("No LLM configs found — ROUTA will use ACP agent as fallback")
                        }
                    }

                    // Set available ACP agents for CRAFTERs
                    crafterSection.setAvailableModels(agentKeys)

                    // Prefer "claude" as default agent, fallback to config.activeAgent or first available
                    val defaultAgent = when {
                        agentKeys.any { it.equals("claude", ignoreCase = true) } ->
                            agentKeys.first { it.equals("claude", ignoreCase = true) }
                        config.activeAgent != null && agentKeys.contains(config.activeAgent) ->
                            config.activeAgent
                        else -> agentKeys.firstOrNull()
                    }

                    if (defaultAgent != null) {
                        crafterSection.setSelectedModel(defaultAgent)
                        if (useAcpForRouta) {
                            routaSection.setSelectedModel(defaultAgent)
                        }

                        // Update the agent popup in the unified input panel
                        updateAgentPopup(agentKeys, defaultAgent)

                        // Initialize the Routa service with the default agent
                        val routaMode = if (useAcpForRouta) "ACP Agent ($defaultAgent)" else "KoogAgent"
                        log.info("Initializing Routa service: CRAFTER=$defaultAgent, ROUTA=$routaMode")
                        routaService.initialize(
                            crafterAgent = defaultAgent,
                            routaAgent = defaultAgent,
                            gateAgent = defaultAgent,
                        )

                        val routaInfo = if (useAcpForRouta) {
                            "ROUTA: $defaultAgent (ACP Agent)"
                        } else {
                            val llmConfig = routaService.getActiveLlmConfig()
                            if (llmConfig != null) {
                                "ROUTA: ${llmConfig.provider}/${llmConfig.model} (KoogAgent)"
                            } else {
                                "ROUTA: fallback to ACP"
                            }
                        }
                        routaSection.setPlanningText("✓ Ready. CRAFTER: $defaultAgent | $routaInfo")
                    } else {
                        log.warn("No default agent found")
                        routaSection.setPlanningText("⚠️ No default agent configured")
                        updateAgentPopup(agentKeys, null)
                    }

                    log.info("Dispatcher ready. ${agentKeys.size} ACP agent(s): ${agentKeys.joinToString(", ")}")
                }
            } catch (e: Exception) {
                log.warn("Failed to load agents: ${e.message}", e)
                withContext(Dispatchers.EDT) {
                    routaSection.setPlanningText("❌ Failed to load agents: ${e.message}")
                }
            }
        }
    }

    // ── Routa Service Observation ───────────────────────────────────────

    private fun observeRoutaService() {
        // Observe orchestration phases
        scope.launch {
            routaService.phase.collectLatest { phase ->
                handlePhaseChange(phase)
            }
        }

        // Observe coordination state (for phase mapping to UI)
        scope.launch {
            routaService.coordinationState.collectLatest { state ->
                routaSection.updatePhase(state.phase)

                // Update GATE section based on phase
                when (state.phase) {
                    CoordinationPhase.VERIFYING -> gateSection.updateStatus(true)
                    CoordinationPhase.COMPLETED -> gateSection.updateStatus(false)
                    else -> {}
                }
            }
        }

        // Observe ROUTA streaming chunks
        scope.launch {
            routaService.routaChunks.collect { chunk ->
                routaSection.appendChunk(chunk)
            }
        }

        // Observe GATE streaming chunks
        scope.launch {
            routaService.gateChunks.collect { chunk ->
                gateSection.appendChunk(chunk)
            }
        }

        // Observe CRAFTER streaming chunks (for real-time output)
        scope.launch {
            routaService.crafterChunks.collect { (agentId, chunk) ->
                crafterSection.appendChunk(agentId, chunk)
            }
        }

        // Observe CRAFTER states
        scope.launch {
            routaService.crafterStates.collectLatest { states ->
                crafterSection.updateCrafterStates(states)
            }
        }

        // Observe MCP server URL - only update bottom status UI (removed from ROUTA and CRAFTER sections)
        scope.launch {
            routaService.mcpServerUrl.collectLatest { url ->
                // Update the MCP status UI components at the bottom
                withContext(Dispatchers.EDT) {
                    currentMcpUrl = url

                    if (url != null) {
                        mcpUrlLabel.text = url
                        mcpUrlLabel.foreground = JBColor(0x589DF6, 0x589DF6) // Blue for clickable link
                        mcpUrlLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        mcpStatusLabel.foreground = JBColor.GRAY // Gray until checked
                        mcpStatusLabel.toolTipText = "Click refresh to check status"
                        mcpRefreshButton.isEnabled = true

                        // Detect and display transport type
                        val transportInfo = detectMcpTransportType(url)
                        mcpTransportLabel.text = transportInfo
                        mcpTransportLabel.toolTipText = getTransportTooltip(url)

                        // Auto-check status when URL becomes available
                        checkMcpServerStatus()
                    } else {
                        mcpUrlLabel.text = "MCP Server: not running"
                        mcpUrlLabel.foreground = JBColor.GRAY
                        mcpUrlLabel.cursor = Cursor.getDefaultCursor()
                        mcpStatusLabel.foreground = JBColor.GRAY
                        mcpStatusLabel.toolTipText = "MCP Server not started"
                        mcpRefreshButton.isEnabled = false
                        mcpTransportLabel.text = ""
                        mcpTransportLabel.toolTipText = null
                    }
                }
            }
        }

        // Observe running state (for UI enable/disable)
        scope.launch {
            routaService.isRunning.collectLatest { running ->
                // Could disable input while running
            }
        }
    }

    // ── Phase Handling ──────────────────────────────────────────────────

    private fun handlePhaseChange(phase: OrchestratorPhase) {
        when (phase) {
            is OrchestratorPhase.Initializing -> {
                routaSection.updatePhase(CoordinationPhase.IDLE)
            }

            is OrchestratorPhase.Planning -> {
                routaSection.updatePhase(CoordinationPhase.PLANNING)
                routaSection.clear()
                crafterSection.clear()
                gateSection.clear()
            }

            is OrchestratorPhase.PlanReady -> {
                routaSection.updatePhase(CoordinationPhase.READY)
                routaSection.setPlanningText(phase.planOutput)
            }

            is OrchestratorPhase.TasksRegistered -> {
                log.info("${phase.count} tasks registered")
            }

            is OrchestratorPhase.WaveStarting -> {
                routaSection.updatePhase(CoordinationPhase.EXECUTING)
            }

            is OrchestratorPhase.CrafterRunning -> {
                // CrafterSectionPanel handles this via crafterStates
            }

            is OrchestratorPhase.CrafterCompleted -> {
                // CrafterSectionPanel handles this via crafterStates
            }

            is OrchestratorPhase.VerificationStarting -> {
                gateSection.updateStatus(true)
                gateSection.clear()
            }

            is OrchestratorPhase.VerificationCompleted -> {
                gateSection.updateStatus(false)
            }

            is OrchestratorPhase.NeedsFix -> {
                routaSection.updatePhase(CoordinationPhase.NEEDS_FIX)
            }

            is OrchestratorPhase.Completed -> {
                routaSection.updatePhase(CoordinationPhase.COMPLETED)
            }

            is OrchestratorPhase.MaxWavesReached -> {
                routaSection.updatePhase(CoordinationPhase.COMPLETED)
            }
        }
    }

    // ── Execution ───────────────────────────────────────────────────────

    private fun startExecution(userInput: String) {
        if (routaService.isRunning.value) {
            log.info("Already running, ignoring request")
            return
        }

        // Check if service is initialized
        if (!routaService.isInitialized()) {
            log.warn("Service not initialized yet, please wait for agents to load")
            routaSection.setPlanningText("⚠️ Service not initialized. Please wait for agents to load or check your configuration.")
            return
        }

        // Clear all panels
        routaSection.clear()
        crafterSection.clear()
        gateSection.clear()

        scope.launch {
            try {
                val result = routaService.execute(userInput)
                handleResult(result)
            } catch (e: Exception) {
                log.warn("Execution failed: ${e.message}", e)
                routaSection.updatePhase(CoordinationPhase.FAILED)
                routaSection.setPlanningText("Execution failed: ${e.message}")
            }
        }
    }

    private fun stopExecution() {
        log.info("Stopping execution...")
        routaService.stopExecution()
        routaSection.appendChunk(
            com.phodal.routa.core.provider.StreamChunk.Text("\n\n⏹ Execution stopped by user.")
        )
    }

    private fun handleResult(result: OrchestratorResult) {
        when (result) {
            is OrchestratorResult.Success -> {
                routaSection.updatePhase(CoordinationPhase.COMPLETED)
                val summary = result.taskSummaries.joinToString("\n") { task ->
                    "  ${task.title}: ${task.status} (verdict: ${task.verdict ?: "N/A"})"
                }
                routaSection.appendChunk(
                    com.phodal.routa.core.provider.StreamChunk.Text("\n\n--- Results ---\n$summary")
                )

                // Update GATE verdict from task summaries
                val allApproved = result.taskSummaries.all {
                    it.verdict == com.phodal.routa.core.model.VerificationVerdict.APPROVED
                }
                if (allApproved) {
                    gateSection.setVerdict(com.phodal.routa.core.model.VerificationVerdict.APPROVED)
                }
            }

            is OrchestratorResult.NoTasks -> {
                routaSection.setPlanningText("No tasks generated from the plan:\n${result.planOutput}")
            }

            is OrchestratorResult.MaxWavesReached -> {
                routaSection.appendChunk(
                    com.phodal.routa.core.provider.StreamChunk.Text(
                        "\n\nMax waves (${result.waves}) reached. Some tasks may be incomplete."
                    )
                )
            }

            is OrchestratorResult.Failed -> {
                routaSection.updatePhase(CoordinationPhase.FAILED)
                routaSection.setPlanningText("Orchestration failed: ${result.error}")
            }
        }
    }

    // ── MCP Server Status ───────────────────────────────────────────────

    /**
     * Detect MCP transport type from URL.
     *
     * Returns a short label indicating the available transports:
     * - "WS + SSE" - WebSocket at /mcp + Legacy SSE at /sse (RoutaMcpWebSocketServer)
     * - "HTTP + WS + SSE" - Streamable HTTP + WebSocket + Legacy SSE (RoutaMcpStreamableHttpServer)
     */
    private fun detectMcpTransportType(url: String): String {
        return when {
            // If URL ends with /mcp, it's the WebSocket endpoint
            // The server also provides /sse for legacy SSE
            url.endsWith("/mcp") -> "[WS + SSE]"

            // If URL ends with /sse, it's the legacy SSE endpoint
            // The server also provides /mcp for WebSocket
            url.endsWith("/sse") -> "[SSE + WS]"

            // Future: detect Streamable HTTP server
            // This would require checking server capabilities or a different URL pattern
            else -> "[Unknown]"
        }
    }

    /**
     * Get detailed tooltip for transport type.
     */
    private fun getTransportTooltip(url: String): String {
        return when {
            url.endsWith("/mcp") -> """
                Available transports:
                • WebSocket: ws://...${url.substringAfter("http://")}
                • Legacy SSE: ${url.replace("/mcp", "/sse")}
            """.trimIndent()

            url.endsWith("/sse") -> """
                Available transports:
                • Legacy SSE: $url
                • WebSocket: ws://...${url.replace("/sse", "/mcp").substringAfter("http://")}
            """.trimIndent()

            else -> "MCP Server endpoint"
        }
    }

    private fun openUrlInBrowser(url: String) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        } catch (e: Exception) {
            log.warn("Failed to open URL in browser: $url", e)
        }
    }

    private fun checkMcpServerStatus() {
        val url = currentMcpUrl ?: return

        scope.launch(Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000

                // Get response code - any response (including 4xx/5xx) means server is running
                val responseCode = connection.responseCode

                withContext(Dispatchers.EDT) {
                    // Green if we got ANY response (server is running)
                    // 4xx/5xx just means the endpoint doesn't exist, but server is up
                    updateMcpStatus(url, true, responseCode)
                }

                connection.disconnect()
            } catch (e: Exception) {
                // Red only if we can't connect at all (connection refused, timeout, etc.)
                withContext(Dispatchers.EDT) {
                    updateMcpStatus(url, false, null)
                }
            }
        }
    }

    private fun updateMcpStatus(url: String, isRunning: Boolean, responseCode: Int?) {
        if (isRunning) {
            mcpStatusLabel.foreground = JBColor(0x3FB950, 0x3FB950) // Green
            val statusText = if (responseCode != null) {
                "MCP Server is running (HTTP $responseCode)"
            } else {
                "MCP Server is running"
            }
            mcpStatusLabel.toolTipText = statusText
        } else {
            mcpStatusLabel.foreground = JBColor(0xF85149, 0xF85149) // Red
            mcpStatusLabel.toolTipText = "MCP Server is not responding (connection failed)"
        }
    }

    override fun dispose() {
        routaService.reset()
        scope.cancel()
    }
}
