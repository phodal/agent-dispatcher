package com.github.phodal.acpmanager.claudecode.panels

import com.agentclientprotocol.model.ToolCallStatus
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Information about a task.
 */
data class TaskInfo(
    val id: String,
    val title: String,
    val status: ToolCallStatus,
    val input: String? = null,
    val output: String? = null
)

/**
 * Collapsible panel showing a summary of all tasks.
 * When collapsed, shows task count. When expanded, shows all tasks with their status.
 * Each task can be further expanded to show input/output.
 */
class TaskSummaryPanel(
    initialTasks: List<TaskInfo>,
    accentColor: Color
) : BaseCollapsiblePanel(accentColor, initiallyExpanded = false) {

    private var currentTasks: List<TaskInfo> = initialTasks.toList()
    private val taskPanels = mutableMapOf<String, TaskItemPanel>()

    init {
        updateHeaderText()
    }

    /**
     * Update the list of tasks.
     */
    fun updateTasks(newTasks: List<TaskInfo>) {
        currentTasks = newTasks.toList()
        updateHeaderText()
        updateTaskList()
    }

    /**
     * Update a single task.
     */
    fun updateTask(taskId: String, status: ToolCallStatus, title: String? = null, output: String? = null) {
        val index = currentTasks.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            val task = currentTasks[index]
            val updatedTask = task.copy(
                status = status,
                title = title ?: task.title,
                output = output ?: task.output
            )
            currentTasks = currentTasks.toMutableList().apply { set(index, updatedTask) }
            updateHeaderText()
            taskPanels[taskId]?.updateTask(updatedTask)
        }
    }

    private fun updateHeaderText() {
        val completed = currentTasks.count { it.status == ToolCallStatus.COMPLETED }
        val total = currentTasks.size
        setTitle("📋 Tasks ($completed/$total completed)")
    }

    private fun updateTaskList() {
        if (isExpanded) {
            contentPanel.removeAll()
            taskPanels.clear()

            for (task in currentTasks) {
                val taskPanel = TaskItemPanel(task, headerColor)
                taskPanels[task.id] = taskPanel
                contentPanel.add(taskPanel)
            }

            contentPanel.revalidate()
            contentPanel.repaint()
        }
    }

    override fun updateExpandedState() {
        super.updateExpandedState()
        if (isExpanded) {
            updateTaskList()
        }
    }

    /**
     * Get all tasks.
     */
    fun getTasks(): List<TaskInfo> = currentTasks.toList()
}

/**
 * Individual task item panel with collapsible input/output.
 */
class TaskItemPanel(
    private var task: TaskInfo,
    private val accentColor: Color
) : JPanel() {

    private val statusLabel: JBLabel
    private val inputSection: CollapsibleSection
    private val outputSection: CollapsibleSection

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(2, 0)
        alignmentX = Component.LEFT_ALIGNMENT

        // Status line
        statusLabel = JBLabel(getStatusText()).apply {
            foreground = getStatusColor()
            font = font.deriveFont(font.size2D - 1)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        add(statusLabel)

        // Input section
        inputSection = CollapsibleSection("📥 Input", accentColor).apply {
            isVisible = task.input?.isNotEmpty() == true
        }
        task.input?.let { inputSection.setContent(it) }
        add(inputSection)

        // Output section
        outputSection = CollapsibleSection("📤 Output", accentColor).apply {
            isVisible = task.output?.isNotEmpty() == true
        }
        task.output?.let { outputSection.setContent(it) }
        add(outputSection)
    }

    private fun getStatusIcon(): String = when (task.status) {
        ToolCallStatus.COMPLETED -> "✓"
        ToolCallStatus.FAILED -> "✗"
        ToolCallStatus.IN_PROGRESS -> "▶"
        else -> "○"
    }

    private fun getStatusColor(): Color = when (task.status) {
        ToolCallStatus.COMPLETED -> JBColor(Color(0x2E7D32), Color(0x81C784))
        ToolCallStatus.FAILED -> JBColor.RED
        else -> accentColor
    }

    private fun getStatusText(): String = "${getStatusIcon()} ${task.title}"

    fun updateTask(newTask: TaskInfo) {
        task = newTask
        statusLabel.text = getStatusText()
        statusLabel.foreground = getStatusColor()

        task.input?.let {
            inputSection.setContent(it)
            inputSection.isVisible = it.isNotEmpty()
        }

        task.output?.let {
            outputSection.setContent(it)
            outputSection.isVisible = it.isNotEmpty()
        }

        revalidate()
        repaint()
    }

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }

    override fun getMinimumSize(): Dimension {
        val pref = preferredSize
        return Dimension(0, pref.height)
    }
}

