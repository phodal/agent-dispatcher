package com.github.phodal.acpmanager.claudecode.handlers

import com.agentclientprotocol.model.ToolCallStatus
import com.github.phodal.acpmanager.claudecode.context.RenderContext
import com.github.phodal.acpmanager.claudecode.panels.TaskInfo
import com.github.phodal.acpmanager.claudecode.panels.TaskSummaryPanel
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.github.phodal.acpmanager.ui.renderer.TaskItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFileManager
import kotlin.reflect.KClass

private val log = logger<TaskEventHandler>()

/**
 * Handler for Task tool calls.
 * Tasks are displayed in a collapsible summary panel at the top.
 * Also emits TaskUpdate events for external UI components (e.g., task status above input).
 */
class TaskEventHandler : MultiEventHandler() {

    companion object {
        private const val TASK_SUMMARY_PANEL_ID = "task-summary"
    }

    // Track tasks internally
    private val tasks = mutableListOf<TaskInfo>()

    override val supportedEvents: Set<KClass<out RenderEvent>> = setOf(
        RenderEvent.ToolCallStart::class,
        RenderEvent.ToolCallUpdate::class,
        RenderEvent.ToolCallEnd::class
    )

    override fun canHandle(event: RenderEvent): Boolean {
        // Only handle Task tool calls
        return when (event) {
            is RenderEvent.ToolCallStart -> isTaskToolCall(event)
            is RenderEvent.ToolCallUpdate -> isTrackedTask(event.toolCallId)
            is RenderEvent.ToolCallEnd -> isTrackedTask(event.toolCallId)
            else -> false
        }
    }

    override fun handle(event: RenderEvent, context: RenderContext) {
        when (event) {
            is RenderEvent.ToolCallStart -> handleStart(event, context)
            is RenderEvent.ToolCallUpdate -> handleUpdate(event, context)
            is RenderEvent.ToolCallEnd -> handleEnd(event, context)
            else -> {}
        }
    }

    private fun isTaskToolCall(event: RenderEvent.ToolCallStart): Boolean {
        return event.kind?.equals("Task", ignoreCase = true) == true ||
               event.toolName.equals("Task", ignoreCase = true)
    }

    private fun isTrackedTask(toolCallId: String): Boolean {
        return tasks.any { it.id == toolCallId }
    }

    private fun handleStart(event: RenderEvent.ToolCallStart, context: RenderContext) {
        val taskInfo = TaskInfo(
            id = event.toolCallId,
            title = event.title ?: "Task",
            status = ToolCallStatus.IN_PROGRESS
        )
        tasks.add(taskInfo)
        updateTaskSummary(context)
        emitTaskUpdate(context)
        context.scrollToBottom()
    }

    private fun handleUpdate(event: RenderEvent.ToolCallUpdate, context: RenderContext) {
        val index = tasks.indexOfFirst { it.id == event.toolCallId }
        if (index >= 0) {
            tasks[index] = tasks[index].copy(
                status = event.status,
                title = event.title ?: tasks[index].title
            )
            updateTaskSummary(context)
            emitTaskUpdate(context)
        }
    }

    private fun handleEnd(event: RenderEvent.ToolCallEnd, context: RenderContext) {
        val index = tasks.indexOfFirst { it.id == event.toolCallId }
        if (index >= 0) {
            tasks[index] = tasks[index].copy(
                status = event.status,
                output = event.output
            )
            updateTaskSummary(context)
            emitTaskUpdate(context)

            // Refresh file system when task completes
            if (event.status == ToolCallStatus.COMPLETED || event.status == ToolCallStatus.FAILED) {
                refreshFileSystem(context)
            }
        }
        context.scrollToBottom()
    }

    /**
     * Refresh the IDE's file system to detect any file changes made by the task.
     */
    private fun refreshFileSystem(context: RenderContext) {
        val project = context.project
        if (project != null && !project.isDisposed) {
            ApplicationManager.getApplication().invokeLater {
                log.info("Refreshing file system after task completion")
                VirtualFileManager.getInstance().asyncRefresh {
                    log.info("File system refresh completed")
                }
            }
        }
    }

    private fun updateTaskSummary(context: RenderContext) {
        if (tasks.isEmpty()) return

        var summaryPanel = context.panelRegistry.get<TaskSummaryPanel>(TASK_SUMMARY_PANEL_ID)

        if (summaryPanel == null) {
            summaryPanel = TaskSummaryPanel(tasks, context.colors.taskFg)
            context.panelRegistry.register(TASK_SUMMARY_PANEL_ID, summaryPanel)
            // Insert at the beginning
            context.insertPanel(summaryPanel.component, 0)
        } else {
            summaryPanel.updateTasks(tasks)
        }

        context.contentPanel.revalidate()
        context.contentPanel.repaint()
    }

    /**
     * Emit TaskUpdate event for external UI components.
     */
    private fun emitTaskUpdate(context: RenderContext) {
        val taskItems = tasks.map { TaskItem(it.id, it.title, it.status) }
        context.emitEvent(RenderEvent.TaskUpdate(taskItems))
    }

    /**
     * Clear all tracked tasks.
     */
    fun clear() {
        tasks.clear()
    }
}

