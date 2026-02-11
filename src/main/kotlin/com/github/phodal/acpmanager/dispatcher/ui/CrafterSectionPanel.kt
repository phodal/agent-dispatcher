package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.dispatcher.routa.CrafterStreamState
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.phodal.routa.core.model.AgentStatus
import com.phodal.routa.core.provider.StreamChunk
import com.phodal.routa.core.provider.ToolCallStatus
import java.awt.*
import javax.swing.*

/**
 * CRAFTERs section — the main focus area of the DAG UI.
 *
 * Shows:
 * - Header with "CRAFTERs" label, active count, and ACP model config
 * - Tabbed interface: one tab per active CRAFTER agent
 * - Each tab shows a CrafterDetailPanel with task info and streaming output
 *
 * This is the largest section since CRAFTERs do the actual implementation work.
 */
class CrafterSectionPanel : JPanel(BorderLayout()) {

    companion object {
        val CRAFTER_ACCENT = JBColor(0x10B981, 0x10B981)
        val CRAFTER_BG = JBColor(0x0D1117, 0x0D1117)
    }

    private val tabbedPane = JBTabbedPane(JTabbedPane.TOP).apply {
        font = font.deriveFont(11f)
    }

    private val activeCountLabel = JBLabel("0 active").apply {
        foreground = JBColor(0x8B949E, 0x8B949E)
        font = font.deriveFont(10f)
    }

    private val mcpUrlLabel = JBLabel("").apply {
        foreground = JBColor(0x58A6FF, 0x58A6FF)  // blue link-style
        font = font.deriveFont(9f)
        toolTipText = "MCP Server SSE endpoint for Claude Code coordination tools"
        isVisible = false
    }

    private val modelCombo = JComboBox<String>().apply {
        preferredSize = Dimension(160, 24)
        font = font.deriveFont(11f)
        toolTipText = "ACP Model for CRAFTER agents"
    }

    /** Callback when the model is changed. */
    var onModelChanged: (String) -> Unit = {}

    /** Maps agentId → CrafterDetailPanel for updating. */
    private val detailPanels = mutableMapOf<String, CrafterDetailPanel>()

    // DAG connectors
    private val dagUpConnector = createDagConnector()
    private val dagDownConnector = createDagConnector()

    init {
        isOpaque = true
        background = CRAFTER_BG
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineBottom(JBColor(0x21262D, 0x21262D)),
            JBUI.Borders.empty(4, 12)
        )

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(JBLabel(AllIcons.Nodes.Gvariable).apply {
                    toolTipText = "CRAFTER Agents"
                })
                add(JBLabel("CRAFTERs").apply {
                    foreground = CRAFTER_ACCENT
                    font = font.deriveFont(Font.BOLD, 12f)
                })
                add(JBLabel("│").apply { foreground = JBColor(0x30363D, 0x30363D) })
                add(activeCountLabel)
                add(mcpUrlLabel)
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(JBLabel("Model:").apply {
                    foreground = JBColor(0x8B949E, 0x8B949E)
                    font = font.deriveFont(10f)
                })
                add(modelCombo)
            }
            add(rightPanel, BorderLayout.EAST)
        }

        // Empty state
        val emptyPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(JBLabel("Waiting for ROUTA to plan tasks...").apply {
                foreground = JBColor(0x6B7280, 0x6B7280)
                font = font.deriveFont(Font.ITALIC, 12f)
            })
        }

        // Initially show empty state in tabbed pane
        tabbedPane.addTab("No CRAFTERs yet", emptyPanel)

        // Layout: top connector + header + tabs + bottom connector
        val mainContent = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(headerPanel, BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
        }

        add(dagUpConnector, BorderLayout.NORTH)
        add(mainContent, BorderLayout.CENTER)
        add(dagDownConnector, BorderLayout.SOUTH)

        // Wire model combo
        modelCombo.addActionListener {
            val selected = modelCombo.selectedItem as? String ?: return@addActionListener
            onModelChanged(selected)
        }
    }

    /**
     * Set the MCP server URL to display.
     */
    fun setMcpServerUrl(url: String?) {
        SwingUtilities.invokeLater {
            if (url != null) {
                mcpUrlLabel.text = "│ MCP: $url"
                mcpUrlLabel.isVisible = true
            } else {
                mcpUrlLabel.text = ""
                mcpUrlLabel.isVisible = false
            }
        }
    }

    /**
     * Set available ACP models for CRAFTERs.
     */
    fun setAvailableModels(models: List<String>) {
        modelCombo.removeAllItems()
        models.forEach { modelCombo.addItem(it) }
    }

    /**
     * Set the selected model.
     */
    fun setSelectedModel(model: String) {
        modelCombo.selectedItem = model
    }

    /**
     * Update all CRAFTER states at once.
     * Creates/updates/removes tabs as needed.
     */
    fun updateCrafterStates(states: Map<String, CrafterStreamState>) {
        SwingUtilities.invokeLater {
            val activeCount = states.values.count { it.status == AgentStatus.ACTIVE }
            val totalCount = states.size
            activeCountLabel.text = "$activeCount/$totalCount active"

            // Remove empty state tab if crafters exist
            if (states.isNotEmpty() && tabbedPane.tabCount == 1 && detailPanels.isEmpty()) {
                tabbedPane.removeAll()
            }

            // Add or update tabs
            for ((agentId, state) in states) {
                val existing = detailPanels[agentId]
                if (existing != null) {
                    existing.update(state)
                    // Update tab title
                    val tabIdx = tabbedPane.indexOfComponent(existing)
                    if (tabIdx >= 0) {
                        tabbedPane.setTitleAt(tabIdx, buildTabTitle(state))
                        tabbedPane.setIconAt(tabIdx, getStatusIcon(state.status))
                    }
                } else {
                    // Create new tab
                    val panel = CrafterDetailPanel()
                    panel.update(state)
                    detailPanels[agentId] = panel
                    tabbedPane.addTab(
                        buildTabTitle(state),
                        getStatusIcon(state.status),
                        panel,
                    )
                    // Auto-select new tab
                    tabbedPane.selectedComponent = panel
                }
            }

            revalidate()
            repaint()
        }
    }

    /**
     * Append a streaming chunk to a specific CRAFTER's panel.
     */
    fun appendChunk(agentId: String, chunk: StreamChunk) {
        SwingUtilities.invokeLater {
            detailPanels[agentId]?.appendChunk(chunk)
        }
    }

    /**
     * Clear all tabs and reset.
     */
    fun clear() {
        SwingUtilities.invokeLater {
            tabbedPane.removeAll()
            detailPanels.clear()
            activeCountLabel.text = "0 active"
            mcpUrlLabel.text = ""
            mcpUrlLabel.isVisible = false
            tabbedPane.addTab("No CRAFTERs yet", JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(JBLabel("Waiting for ROUTA to plan tasks...").apply {
                    foreground = JBColor(0x6B7280, 0x6B7280)
                    font = font.deriveFont(Font.ITALIC, 12f)
                })
            })
        }
    }

    private fun buildTabTitle(state: CrafterStreamState): String {
        val title = state.taskTitle.ifBlank { state.taskId }
        return if (title.length > 20) title.take(18) + "…" else title
    }

    private fun getStatusIcon(status: AgentStatus): Icon = when (status) {
        AgentStatus.ACTIVE -> AllIcons.Process.Step_1
        AgentStatus.COMPLETED -> AllIcons.RunConfigurations.TestPassed
        AgentStatus.ERROR -> AllIcons.RunConfigurations.TestFailed
        AgentStatus.CANCELLED -> AllIcons.RunConfigurations.TestIgnored
        AgentStatus.PENDING -> AllIcons.RunConfigurations.TestNotRan
    }

    private fun createDagConnector(): JPanel {
        return object : JPanel() {
            init {
                isOpaque = false
                preferredSize = Dimension(0, 10)
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBColor(0x30363D, 0x30363D)
                val cx = width / 2
                g2.drawLine(cx, 0, cx, height)
            }
        }
    }
}

/**
 * Individual CRAFTER detail panel — shown inside a tab.
 *
 * Shows:
 * - Task info: title, objective, scope, acceptance criteria
 * - Status bar with progress
 * - Streaming output area (main content)
 */
class CrafterDetailPanel : JPanel(BorderLayout()) {

    private val taskTitleLabel = JBLabel("").apply {
        foreground = JBColor(0xC9D1D9, 0xC9D1D9)
        font = font.deriveFont(Font.BOLD, 12f)
    }

    private val taskIdLabel = JBLabel("").apply {
        foreground = JBColor(0x6B7280, 0x6B7280)
        font = font.deriveFont(9f)
    }

    private val statusLabel = JBLabel("PENDING").apply {
        foreground = JBColor(0x6B7280, 0x6B7280)
        font = font.deriveFont(Font.BOLD, 10f)
    }

    private val progressBar = JProgressBar(0, 100).apply {
        value = 0
        preferredSize = Dimension(0, 3)
        isStringPainted = false
        background = JBColor(0x21262D, 0x21262D)
        foreground = CrafterSectionPanel.CRAFTER_ACCENT
    }

    // Streaming output — the main content area
    private val outputArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBColor(0x0D1117, 0x0D1117)
        foreground = JBColor(0xC0C0C0, 0xA0A0A0)
        font = Font("Monospaced", Font.PLAIN, 11)
        border = JBUI.Borders.empty(4)
    }

    private val outputScroll = JScrollPane(outputArea).apply {
        border = BorderFactory.createLineBorder(JBColor(0x21262D, 0x21262D))
    }

    // Task details (collapsible)
    private val taskDetailsArea = JTextArea(3, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBColor(0x161B22, 0x161B22)
        foreground = JBColor(0x8B949E, 0x8B949E)
        font = Font("SansSerif", Font.PLAIN, 11)
        border = JBUI.Borders.empty(4)
    }

    private val taskDetailsScroll = JScrollPane(taskDetailsArea).apply {
        border = BorderFactory.createLineBorder(JBColor(0x21262D, 0x21262D))
        preferredSize = Dimension(0, 60)
        isVisible = false
    }

    private val detailsToggle = JBLabel("▶ Task Details").apply {
        foreground = JBColor(0x6B7280, 0x6B7280)
        font = font.deriveFont(Font.BOLD, 10f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    // Tool call tracking
    private val toolCallPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(2, 0)
    }

    private var detailsExpanded = false

    init {
        isOpaque = true
        background = JBColor(0x0D1117, 0x0D1117)
        border = JBUI.Borders.empty(4)

        // Top: task info + status
        val infoPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)

            val leftPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(taskTitleLabel)
                add(Box.createVerticalStrut(2))
                add(taskIdLabel)
            }
            add(leftPanel, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
        }

        // Task details section (collapsible)
        val detailsSection = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 0)
            add(detailsToggle, BorderLayout.NORTH)
            add(taskDetailsScroll, BorderLayout.CENTER)
        }

        detailsToggle.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                detailsExpanded = !detailsExpanded
                taskDetailsScroll.isVisible = detailsExpanded
                detailsToggle.text = if (detailsExpanded) "▼ Task Details" else "▶ Task Details"
                revalidate()
                repaint()
            }
        })

        // Top section: info + progress + details
        val topSection = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(infoPanel)
            add(progressBar)
            add(Box.createVerticalStrut(4))
            add(detailsSection)
            add(toolCallPanel)
        }

        // Split: top info + bottom streaming output
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = topSection
            bottomComponent = outputScroll
            dividerLocation = 80
            resizeWeight = 0.2
            border = JBUI.Borders.empty()
        }

        add(splitPane, BorderLayout.CENTER)
    }

    /**
     * Update the full state of this CRAFTER.
     */
    fun update(state: CrafterStreamState) {
        taskTitleLabel.text = state.taskTitle.ifBlank { "Task ${state.taskId.take(8)}" }
        taskIdLabel.text = "Agent: ${state.agentId.take(8)}  |  Task: ${state.taskId.take(8)}"

        val (statusText, statusColor) = when (state.status) {
            AgentStatus.PENDING -> "PENDING" to JBColor(0x6B7280, 0x6B7280)
            AgentStatus.ACTIVE -> "RUNNING" to JBColor(0x3B82F6, 0x3B82F6)
            AgentStatus.COMPLETED -> "COMPLETED" to CrafterSectionPanel.CRAFTER_ACCENT
            AgentStatus.ERROR -> "ERROR" to JBColor(0xEF4444, 0xEF4444)
            AgentStatus.CANCELLED -> "CANCELLED" to JBColor(0xF59E0B, 0xF59E0B)
        }
        statusLabel.text = statusText
        statusLabel.foreground = statusColor

        progressBar.value = when (state.status) {
            AgentStatus.COMPLETED -> 100
            AgentStatus.ACTIVE -> 50
            AgentStatus.ERROR -> progressBar.value
            else -> 0
        }
        progressBar.foreground = statusColor

        // Don't replace output text on update (streaming handles that)
        if (outputArea.text.isEmpty() && state.outputText.isNotEmpty()) {
            outputArea.text = state.outputText
            outputArea.caretPosition = outputArea.document.length
        }
    }

    /**
     * Append a streaming chunk to the output.
     */
    fun appendChunk(chunk: StreamChunk) {
        when (chunk) {
            is StreamChunk.Text -> {
                outputArea.append(chunk.content)
                trimOutput()
                outputArea.caretPosition = outputArea.document.length
            }

            is StreamChunk.ToolCall -> {
                addToolCallEntry(chunk)
            }

            is StreamChunk.Error -> {
                outputArea.append("\n[ERROR] ${chunk.message}\n")
                outputArea.caretPosition = outputArea.document.length
            }

            is StreamChunk.Completed -> {
                statusLabel.text = "COMPLETED"
                statusLabel.foreground = CrafterSectionPanel.CRAFTER_ACCENT
                progressBar.value = 100
                progressBar.foreground = CrafterSectionPanel.CRAFTER_ACCENT
            }

            else -> {}
        }
    }

    /**
     * Set the task details text (objective, scope, criteria).
     */
    fun setTaskDetails(details: String) {
        taskDetailsArea.text = details
        taskDetailsArea.caretPosition = 0
    }

    private fun addToolCallEntry(toolCall: StreamChunk.ToolCall) {
        val icon = when (toolCall.status) {
            ToolCallStatus.STARTED -> AllIcons.Process.Step_1
            ToolCallStatus.IN_PROGRESS -> AllIcons.Process.Step_4
            ToolCallStatus.COMPLETED -> AllIcons.RunConfigurations.TestPassed
            ToolCallStatus.FAILED -> AllIcons.RunConfigurations.TestFailed
        }
        val label = JBLabel("${toolCall.name}${if (toolCall.result != null) " → ${toolCall.result!!.take(50)}" else ""}").apply {
            this.icon = icon
            foreground = JBColor(0x8B949E, 0x8B949E)
            font = font.deriveFont(10f)
        }

        // Keep only last 10 tool calls
        if (toolCallPanel.componentCount > 10) {
            toolCallPanel.remove(0)
        }
        toolCallPanel.add(label)
        toolCallPanel.revalidate()
        toolCallPanel.repaint()
    }

    private fun trimOutput() {
        val doc = outputArea.document
        val maxChars = 10000
        if (doc.length > maxChars) {
            val removeLen = doc.length - maxChars
            doc.remove(0, removeLen)
        }
    }
}
