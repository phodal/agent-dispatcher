package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.dispatcher.model.*
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Tasks panel — the middle section of the multi-agent dispatcher UI.
 *
 * Shows:
 * - List of tasks from the plan
 * - Per-task agent assignment (editable combo box)
 * - Progress bars per task
 * - Execute button to start the plan
 * - Active agent count badge
 */
class TaskListPanel : JPanel(BorderLayout()) {

    private val taskCards = mutableListOf<TaskCardPanel>()
    private val tasksContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val headerLabel = JBLabel("TASKS  0/0").apply {
        foreground = JBColor(0x8B949E, 0x8B949E)
        font = font.deriveFont(Font.BOLD, 11f)
    }
    private val agentBadge = JBLabel("0/0 active").apply {
        foreground = JBColor(0x8B949E, 0x8B949E)
        font = font.deriveFont(11f)
    }
    private val executeButton = JButton("Execute Plan").apply {
        icon = AllIcons.Actions.Execute
        isEnabled = false
    }
    private val parallelismSpinner = JSpinner(SpinnerNumberModel(1, 1, 5, 1)).apply {
        preferredSize = Dimension(60, 28)
        toolTipText = "Maximum parallel tasks"
    }

    var onExecute: () -> Unit = {}
    var onTaskAgentChanged: (taskId: String, newAgent: String) -> Unit = { _, _ -> }
    var onParallelismChanged: (Int) -> Unit = {}

    private var availableAgents: List<String> = emptyList()

    init {
        isOpaque = true
        background = JBColor(0x0D1117, 0x0D1117)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineBottom(JBColor(0x21262D, 0x21262D)),
            JBUI.Borders.empty(8, 16)
        )

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(8)

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                add(headerLabel)
                add(JBLabel("│").apply { foreground = JBColor(0x30363D, 0x30363D) })
                add(JBLabel(AllIcons.Nodes.MultipleTypeDefinitions))
                add(agentBadge)
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                isOpaque = false
                add(JBLabel("Parallel:").apply {
                    foreground = JBColor(0x8B949E, 0x8B949E)
                    font = font.deriveFont(11f)
                })
                add(parallelismSpinner)
                add(executeButton)
            }
            add(rightPanel, BorderLayout.EAST)
        }
        add(headerPanel, BorderLayout.NORTH)

        // Scrollable tasks area
        val scrollPane = JBScrollPane(tasksContainer).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        add(scrollPane, BorderLayout.CENTER)

        // Wire up events
        executeButton.addActionListener { onExecute() }
        parallelismSpinner.addChangeListener {
            val value = parallelismSpinner.value as Int
            onParallelismChanged(value)
        }
    }

    fun setAvailableAgents(agents: List<String>) {
        this.availableAgents = agents
    }

    fun updateTasks(tasks: List<AgentTask>) {
        tasksContainer.removeAll()
        taskCards.clear()

        for (task in tasks) {
            val card = TaskCardPanel(task, availableAgents)
            card.onAgentChanged = { newAgent ->
                onTaskAgentChanged(task.id, newAgent)
            }
            taskCards.add(card)
            tasksContainer.add(card)
            tasksContainer.add(Box.createVerticalStrut(4))
        }

        val done = tasks.count { it.status == AgentTaskStatus.DONE }
        headerLabel.text = "TASKS  $done/${tasks.size}"
        executeButton.isEnabled = tasks.isNotEmpty()

        tasksContainer.revalidate()
        tasksContainer.repaint()
    }

    fun updateActiveAgents(active: Int, total: Int) {
        agentBadge.text = "$active/$total active"
    }

    fun setParallelism(value: Int) {
        parallelismSpinner.value = value
    }

    fun setExecuteEnabled(enabled: Boolean) {
        executeButton.isEnabled = enabled
    }
}

/**
 * A task card showing task title, assigned agent, progress, and status.
 */
class TaskCardPanel(
    private val task: AgentTask,
    private val availableAgents: List<String>,
) : JPanel(BorderLayout()) {

    private val progressBar = JProgressBar(0, 100).apply {
        value = task.progress
        preferredSize = Dimension(0, 4)
        isStringPainted = false
        background = JBColor(0x21262D, 0x21262D)
        foreground = JBColor(0x10B981, 0x10B981)
    }

    private val agentCombo = JComboBox<String>().apply {
        preferredSize = Dimension(140, 24)
    }

    var onAgentChanged: (String) -> Unit = {}

    init {
        isOpaque = true
        background = JBColor(0x161B22, 0x161B22)
        border = JBUI.Borders.compound(
            BorderFactory.createLineBorder(JBColor(0x21262D, 0x21262D)),
            JBUI.Borders.empty(8, 12)
        )
        maximumSize = Dimension(Int.MAX_VALUE, 90)

        // Top row: status icon + title + progress percentage
        val topRow = JPanel(BorderLayout()).apply {
            isOpaque = false

            val statusIcon = createStatusIcon(task.status)
            val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(statusIcon)
                add(JBLabel(task.title).apply {
                    foreground = JBColor(0xC9D1D9, 0xC9D1D9)
                    font = font.deriveFont(Font.BOLD, 13f)
                })
            }
            add(titlePanel, BorderLayout.WEST)

            val statusText = task.status.name
            add(JBLabel(statusText).apply {
                foreground = getStatusColor(task.status)
                font = font.deriveFont(11f)
            }, BorderLayout.EAST)
        }

        // Middle row: agent assignment chips
        val agentRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(4)

            // Agent tag
            val agentTag = createAgentTag(task.assignedAgent ?: "unassigned")
            add(agentTag)

            // Agent selector combo
            availableAgents.forEach { agentCombo.addItem(it) }
            task.assignedAgent?.let { agentCombo.selectedItem = it }
            agentCombo.addActionListener {
                val selected = agentCombo.selectedItem as? String ?: return@addActionListener
                onAgentChanged(selected)
            }
            add(agentCombo)
        }

        // Layout
        val contentPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(topRow, BorderLayout.NORTH)
            add(agentRow, BorderLayout.CENTER)
            add(progressBar, BorderLayout.SOUTH)
        }
        add(contentPanel, BorderLayout.CENTER)
    }

    fun updateStatus(status: AgentTaskStatus, progress: Int) {
        progressBar.value = progress
        when (status) {
            AgentTaskStatus.RUNNING -> progressBar.foreground = JBColor(0x10B981, 0x10B981)
            AgentTaskStatus.DONE -> {
                progressBar.foreground = JBColor(0x10B981, 0x10B981)
                progressBar.value = 100
            }
            AgentTaskStatus.FAILED -> progressBar.foreground = JBColor(0xEF4444, 0xEF4444)
            AgentTaskStatus.BLOCKED -> progressBar.foreground = JBColor(0xF59E0B, 0xF59E0B)
            else -> {}
        }
        repaint()
    }

    private fun createStatusIcon(status: AgentTaskStatus): JBLabel {
        return when (status) {
            AgentTaskStatus.DONE -> JBLabel(AllIcons.RunConfigurations.TestPassed)
            AgentTaskStatus.RUNNING -> JBLabel(AllIcons.Process.Step_1)
            AgentTaskStatus.FAILED -> JBLabel(AllIcons.RunConfigurations.TestFailed)
            AgentTaskStatus.BLOCKED -> JBLabel(AllIcons.RunConfigurations.TestIgnored)
            AgentTaskStatus.QUEUED -> JBLabel(AllIcons.RunConfigurations.TestNotRan)
            AgentTaskStatus.ACTIVE -> JBLabel(AllIcons.Process.Step_1)
        }
    }

    private fun getStatusColor(status: AgentTaskStatus): Color {
        return when (status) {
            AgentTaskStatus.DONE -> JBColor(0x10B981, 0x10B981)
            AgentTaskStatus.RUNNING, AgentTaskStatus.ACTIVE -> JBColor(0x3B82F6, 0x3B82F6)
            AgentTaskStatus.FAILED -> JBColor(0xEF4444, 0xEF4444)
            AgentTaskStatus.BLOCKED -> JBColor(0xF59E0B, 0xF59E0B)
            AgentTaskStatus.QUEUED -> JBColor(0x6B7280, 0x6B7280)
        }
    }

    private fun createAgentTag(agentId: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = true
            background = JBColor(0x1F6FEB, 0x1F6FEB)
            border = JBUI.Borders.empty(2, 6)
            add(JBLabel("● $agentId").apply {
                foreground = Color.WHITE
                font = font.deriveFont(10f)
            })
        }
    }
}
