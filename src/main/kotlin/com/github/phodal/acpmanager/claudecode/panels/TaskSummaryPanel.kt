package com.github.phodal.acpmanager.claudecode.panels

import com.agentclientprotocol.model.ToolCallStatus
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
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
 * Modern, minimal task summary panel.
 *
 * Design principles:
 * - Compact single-line display when collapsed
 * - Simple task list when expanded
 * - Status shown via icons only, not colors
 */
class TaskSummaryPanel(
    initialTasks: List<TaskInfo>,
    accentColor: Color
) : BaseCollapsiblePanel(accentColor, initiallyExpanded = false) {

    private var currentTasks: List<TaskInfo> = initialTasks.toList()
    private val taskPanels = mutableMapOf<String, TaskItemPanel>()

    init {
        border = JBUI.Borders.empty(4, 8)
        headerTitle.foreground = UIUtil.getLabelForeground()
        headerTitle.font = headerTitle.font.deriveFont(Font.PLAIN)
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
        setTitle("Tasks ($completed/$total completed)")
    }

    private fun updateTaskList() {
        if (isExpanded) {
            contentPanel.removeAll()
            taskPanels.clear()

            for (task in currentTasks) {
                val taskPanel = TaskItemPanel(task)
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
 * Modern, minimal task item display.
 * Shows status icon and title only - no nested sections.
 */
class TaskItemPanel(
    private var task: TaskInfo
) : JPanel() {

    private val statusIcon: JBLabel
    private val titleLabel: JBLabel

    init {
        layout = FlowLayout(FlowLayout.LEFT, 4, 2)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT

        // Status icon
        statusIcon = JBLabel(getStatusIcon()).apply {
            foreground = getStatusIconColor()
            font = font.deriveFont(12f)
        }
        add(statusIcon)

        // Title
        titleLabel = JBLabel(task.title).apply {
            foreground = UIUtil.getLabelForeground()
            font = font.deriveFont(font.size2D - 1)
        }
        add(titleLabel)
    }

    private fun getStatusIcon(): String = when (task.status) {
        ToolCallStatus.COMPLETED -> "✓"
        ToolCallStatus.FAILED -> "✗"
        ToolCallStatus.IN_PROGRESS -> "▶"
        else -> "○"
    }

    private fun getStatusIconColor(): Color = when (task.status) {
        ToolCallStatus.COMPLETED -> JBColor(Color(0x4CAF50), Color(0x81C784))
        ToolCallStatus.FAILED -> JBColor(Color(0xE57373), Color(0xEF5350))
        ToolCallStatus.IN_PROGRESS -> JBColor(Color(0x64B5F6), Color(0x90CAF9))
        else -> UIUtil.getLabelDisabledForeground()
    }

    fun updateTask(newTask: TaskInfo) {
        task = newTask
        statusIcon.text = getStatusIcon()
        statusIcon.foreground = getStatusIconColor()
        titleLabel.text = task.title
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

