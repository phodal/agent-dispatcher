package com.github.phodal.acpmanager.claudecode

import com.agentclientprotocol.model.ToolCallStatus
import com.github.phodal.acpmanager.ui.renderer.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

private val log = logger<ClaudeCodeRenderer>()

/**
 * Custom renderer for Claude Code with Claude-specific styling.
 *
 * Features:
 * - Collapsible thinking panel (2 lines by default)
 * - Unified background colors
 * - Compact tool call display
 */
class ClaudeCodeRenderer(
    private val agentKey: String,
    private val scrollCallback: () -> Unit,
) : AcpEventRenderer {

    // Inner content panel that holds actual messages
    private val contentPanel: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    override val container: JPanel = JPanel(BorderLayout()).apply {
        background = UIUtil.getPanelBackground()
        add(contentPanel, BorderLayout.NORTH) // NORTH ensures content stays at top
    }

    // Streaming state
    private var currentThinkingPanel: SimpleStreamingPanel? = null
    private var currentMessagePanel: SimpleStreamingPanel? = null
    private val thinkingBuffer = StringBuilder()
    private val messageBuffer = StringBuilder()
    private var currentThinkingSignature: String? = null

    // Tool call tracking
    private val toolCallPanels = mutableMapOf<String, SimpleToolCallPanel>()

    // Task tracking - for Task tool calls
    private val tasks = mutableListOf<TaskInfo>()
    private var taskSummaryPanel: TaskSummaryPanel? = null

    // Unified colors - use panel background
    private val panelBg = UIUtil.getPanelBackground()
    private val thinkingFg = UIUtil.getLabelDisabledForeground() // Gray for thinking
    private val messageFg = JBColor(Color(0x2E7D32), Color(0x81C784)) // Green for assistant
    private val userFg = JBColor(Color(0x1565C0), Color(0x64B5F6)) // Blue for user
    private val toolFg = JBColor(Color(0xE65100), Color(0xFFB74D)) // Orange for tools
    private val taskFg = JBColor(Color(0x00695C), Color(0x4DB6AC)) // Teal for tasks

    override fun onEvent(event: RenderEvent) {
        log.info("ClaudeCodeRenderer[$agentKey]: onEvent ${event::class.simpleName}")
        when (event) {
            is RenderEvent.UserMessage -> addUserMessage(event)
            is RenderEvent.ThinkingStart -> startThinking()
            is RenderEvent.ThinkingChunk -> appendThinking(event.content)
            is RenderEvent.ThinkingEnd -> endThinking(event.fullContent)
            is RenderEvent.ThinkingSignature -> onThinkingSignature(event.signature)
            is RenderEvent.MessageStart -> startMessage()
            is RenderEvent.MessageChunk -> appendMessage(event.content)
            is RenderEvent.MessageEnd -> endMessage(event.fullContent)
            is RenderEvent.ToolCallStart -> startToolCall(event)
            is RenderEvent.ToolCallUpdate -> updateToolCall(event)
            is RenderEvent.ToolCallEnd -> endToolCall(event)
            is RenderEvent.ToolCallParameterUpdate -> updateToolCallParameters(event)
            is RenderEvent.PlanUpdate -> addPlanUpdate(event)
            is RenderEvent.ModeChange -> { /* ignore */ }
            is RenderEvent.Info -> addInfo(event)
            is RenderEvent.Error -> addError(event)
            is RenderEvent.Connected -> { /* ignore - don't show connection message */ }
            is RenderEvent.Disconnected -> addInfo(RenderEvent.Info("Disconnected"))
            is RenderEvent.PromptComplete -> onPromptComplete()
        }
    }

    private fun addUserMessage(event: RenderEvent.UserMessage) {
        val panel = createCompactMessagePanel("You", event.content, event.timestamp, userFg)
        addPanel(panel)
        scrollToContentBottom()
    }

    private fun startThinking() {
        thinkingBuffer.clear()
        currentThinkingSignature = null
        val panel = SimpleStreamingPanel("💡 Thinking", thinkingFg)
        currentThinkingPanel = panel
        addPanel(panel)
        scrollToContentBottom()
    }

    private fun appendThinking(content: String) {
        thinkingBuffer.append(content)
        currentThinkingPanel?.updateContent(thinkingBuffer.toString())
    }

    private fun endThinking(fullContent: String) {
        currentThinkingPanel?.finalize(fullContent, currentThinkingSignature)
        currentThinkingPanel = null
        thinkingBuffer.clear()
        currentThinkingSignature = null
        scrollToContentBottom()
    }

    private fun onThinkingSignature(signature: String) {
        currentThinkingSignature = signature
    }

    private fun startMessage() {
        messageBuffer.clear()
        val panel = SimpleStreamingPanel("Assistant", messageFg)
        currentMessagePanel = panel
        addPanel(panel)
        scrollToContentBottom()
    }

    private fun appendMessage(content: String) {
        messageBuffer.append(content)
        currentMessagePanel?.updateContent(messageBuffer.toString())
        scrollToContentBottom()
    }

    private fun endMessage(fullContent: String) {
        currentMessagePanel?.finalize(fullContent, null)
        currentMessagePanel = null
        messageBuffer.clear()
        scrollToContentBottom()
    }

    private fun startToolCall(event: RenderEvent.ToolCallStart) {
        // Check if this is a Task tool call
        val isTask = event.kind?.equals("Task", ignoreCase = true) == true ||
                     event.toolName.equals("Task", ignoreCase = true)

        if (isTask) {
            // Track as a task
            val taskInfo = TaskInfo(
                id = event.toolCallId,
                title = event.title ?: "Task",
                status = ToolCallStatus.IN_PROGRESS
            )
            tasks.add(taskInfo)
            updateTaskSummary()
        } else {
            // Regular tool call
            val panel = SimpleToolCallPanel(event.toolName, event.title ?: event.toolName)
            toolCallPanels[event.toolCallId] = panel
            addPanel(panel)
        }
        scrollToContentBottom()
    }

    private fun updateToolCall(event: RenderEvent.ToolCallUpdate) {
        // Check if this is a task
        val taskIndex = tasks.indexOfFirst { it.id == event.toolCallId }
        if (taskIndex >= 0) {
            tasks[taskIndex] = tasks[taskIndex].copy(
                status = event.status,
                title = event.title ?: tasks[taskIndex].title
            )
            updateTaskSummary()
        } else {
            toolCallPanels[event.toolCallId]?.updateStatus(event.status, event.title)
        }
    }

    private fun updateToolCallParameters(event: RenderEvent.ToolCallParameterUpdate) {
        toolCallPanels[event.toolCallId]?.updateParameters(event.partialParameters)
    }

    private fun endToolCall(event: RenderEvent.ToolCallEnd) {
        // Check if this is a task
        val taskIndex = tasks.indexOfFirst { it.id == event.toolCallId }
        if (taskIndex >= 0) {
            tasks[taskIndex] = tasks[taskIndex].copy(
                status = event.status,
                output = event.output
            )
            updateTaskSummary()
        } else {
            toolCallPanels[event.toolCallId]?.complete(event.status, event.output)
        }
        scrollToContentBottom()
    }

    private fun updateTaskSummary() {
        if (tasks.isEmpty()) return

        if (taskSummaryPanel == null) {
            taskSummaryPanel = TaskSummaryPanel(tasks, taskFg)
            // Insert at the beginning of content panel
            contentPanel.add(taskSummaryPanel, 0)
        } else {
            taskSummaryPanel?.updateTasks(tasks)
        }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun addPlanUpdate(event: RenderEvent.PlanUpdate) {
        // Plan updates are handled separately
    }

    private fun addInfo(event: RenderEvent.Info) {
        val label = JBLabel("ℹ ${event.message}").apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 1)
            border = JBUI.Borders.empty(2, 8)
        }
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(label, BorderLayout.WEST)
        }
        addPanel(panel)
    }

    private fun addError(event: RenderEvent.Error) {
        val label = JBLabel("⚠️ ${event.message}").apply {
            foreground = JBColor.RED
            border = JBUI.Borders.empty(2, 8)
        }
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(label, BorderLayout.WEST)
        }
        addPanel(panel)
    }

    private fun onPromptComplete() {
        log.info("ClaudeCodeRenderer[$agentKey]: Prompt complete")
    }

    private fun scrollToContentBottom() {
        // Only scroll to the actual content, not empty space
        SwingUtilities.invokeLater {
            scrollCallback()
        }
    }

    private fun createCompactMessagePanel(name: String, content: String, timestamp: Long, headerColor: Color): JPanel {
        return object : JPanel() {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(4, 8)

                val header = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(20))
                    add(JBLabel(name).apply {
                        foreground = headerColor
                        font = font.deriveFont(Font.BOLD)
                    }, BorderLayout.WEST)
                    add(JBLabel(SimpleDateFormat("HH:mm:ss").format(Date(timestamp))).apply {
                        foreground = UIUtil.getLabelDisabledForeground()
                        font = font.deriveFont(font.size2D - 2)
                    }, BorderLayout.EAST)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(header)

                val textArea = JTextArea(content).apply {
                    isEditable = false
                    isOpaque = false
                    lineWrap = true
                    wrapStyleWord = true
                    font = UIUtil.getLabelFont()
                    foreground = UIUtil.getLabelForeground()
                    border = JBUI.Borders.emptyTop(2)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(textArea)
            }

            override fun getMaximumSize(): Dimension {
                val pref = preferredSize
                return Dimension(Int.MAX_VALUE, pref.height)
            }
        }
    }

    /**
     * Simple streaming panel - no collapsing, just shows content as it streams.
     */
    inner class SimpleStreamingPanel(private val name: String, private val headerColor: Color) : JPanel() {
        private val headerLabel: JBLabel
        private val contentArea: JTextArea
        private val signatureLabel: JBLabel

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(4, 8)

            headerLabel = JBLabel("$name (streaming...)").apply {
                foreground = headerColor
                font = font.deriveFont(Font.BOLD)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            add(headerLabel)

            contentArea = JTextArea().apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                font = UIUtil.getLabelFont()
                foreground = UIUtil.getLabelForeground()
                border = JBUI.Borders.emptyTop(2)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            add(contentArea)

            signatureLabel = JBLabel().apply {
                foreground = UIUtil.getLabelDisabledForeground()
                font = font.deriveFont(font.size2D - 2)
                border = JBUI.Borders.emptyTop(2)
                alignmentX = Component.LEFT_ALIGNMENT
                isVisible = false
            }
            add(signatureLabel)
        }

        override fun getMaximumSize(): Dimension {
            val pref = preferredSize
            return Dimension(Int.MAX_VALUE, pref.height)
        }

        fun updateContent(content: String) {
            contentArea.text = content
            revalidate()
            repaint()
            parent?.revalidate()
        }

        fun finalize(content: String, signature: String?) {
            headerLabel.text = name
            contentArea.text = content
            signature?.let {
                signatureLabel.text = "✓ Verified (${it.take(8)}...)"
                signatureLabel.isVisible = true
            }
            revalidate()
            repaint()
            parent?.revalidate()
        }
    }

    /**
     * Simple tool call panel - shows tool name, parameters, and status.
     */
    inner class SimpleToolCallPanel(
        private val toolName: String,
        private var title: String
    ) : JPanel() {
        private val statusIcon: JBLabel
        private val titleLabel: JBLabel
        private val parametersArea: JTextArea
        private var isCompleted = false

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(2, 8)

            // Header line with status icon and title
            val headerPanel = JPanel(BorderLayout(4, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
            }

            statusIcon = JBLabel("▶").apply {
                foreground = toolFg
                font = font.deriveFont(Font.BOLD)
            }
            headerPanel.add(statusIcon, BorderLayout.WEST)

            titleLabel = JBLabel("🔧 $title").apply {
                foreground = toolFg
                font = font.deriveFont(Font.BOLD, font.size2D - 1)
            }
            headerPanel.add(titleLabel, BorderLayout.CENTER)

            add(headerPanel)

            // Parameters area (initially hidden)
            parametersArea = JTextArea().apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                font = UIUtil.getLabelFont().deriveFont(font.size2D - 1)
                foreground = UIUtil.getLabelDisabledForeground()
                border = JBUI.Borders.emptyLeft(20)
                alignmentX = Component.LEFT_ALIGNMENT
                isVisible = false
            }
            add(parametersArea)
        }

        override fun getMaximumSize(): Dimension {
            val pref = preferredSize
            return Dimension(Int.MAX_VALUE, pref.height)
        }

        fun updateStatus(status: ToolCallStatus, newTitle: String?) {
            if (isCompleted) return
            newTitle?.let { title = it; titleLabel.text = "🔧 $it" }
            statusIcon.text = when (status) {
                ToolCallStatus.IN_PROGRESS -> "▶"
                ToolCallStatus.PENDING -> "○"
                else -> "■"
            }
            revalidate()
            repaint()
        }

        fun updateParameters(partialParams: String) {
            if (isCompleted) return
            parametersArea.text = partialParams.take(200) // Limit display
            parametersArea.isVisible = partialParams.isNotEmpty()
            revalidate()
            repaint()
            parent?.revalidate()
        }

        fun complete(status: ToolCallStatus, output: String?) {
            isCompleted = true
            val (icon, color) = if (status == ToolCallStatus.COMPLETED) {
                "✓" to JBColor(Color(0x2E7D32), Color(0x81C784))
            } else {
                "✗" to JBColor.RED
            }
            statusIcon.text = icon
            statusIcon.foreground = color
            titleLabel.foreground = color
            // Hide parameters on completion to keep UI clean
            parametersArea.isVisible = false
            revalidate()
            repaint()
        }
    }

    private fun addPanel(panel: JPanel) {
        panel.alignmentX = Component.LEFT_ALIGNMENT
        // Ensure panel has proper sizing
        val prefHeight = panel.preferredSize.height
        panel.maximumSize = Dimension(Int.MAX_VALUE, prefHeight)
        panel.minimumSize = Dimension(0, prefHeight)
        contentPanel.add(panel)
        contentPanel.revalidate()
        contentPanel.repaint()
        container.revalidate()
        container.repaint()
    }

    override fun clear() {
        contentPanel.removeAll()
        currentThinkingPanel = null
        currentMessagePanel = null
        thinkingBuffer.clear()
        messageBuffer.clear()
        toolCallPanels.clear()
        tasks.clear()
        taskSummaryPanel = null
        container.revalidate()
        container.repaint()
    }

    override fun dispose() {
        clear()
    }

    override fun scrollToBottom() {
        scrollCallback()
    }

    override fun getDebugState(): String {
        return "ClaudeCodeRenderer[$agentKey](panels=${contentPanel.componentCount}, tasks=${tasks.size})"
    }

    /**
     * Collapsible Task Summary Panel - shows task count when collapsed, all tasks when expanded.
     */
    inner class TaskSummaryPanel(
        initialTasks: List<TaskInfo>,
        private val accentColor: Color
    ) : JPanel(BorderLayout()) {
        private val headerLabel: JBLabel
        private val taskListPanel: JPanel
        private var isExpanded = false
        private var currentTasks: List<TaskInfo> = initialTasks.toList()

        init {
            isOpaque = false
            border = JBUI.Borders.empty(4, 8)

            headerLabel = JBLabel(getHeaderText()).apply {
                foreground = accentColor
                font = font.deriveFont(Font.BOLD)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = JBUI.Borders.empty(2, 0)
            }

            taskListPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                isVisible = false
                border = JBUI.Borders.emptyLeft(16)
            }

            add(headerLabel, BorderLayout.NORTH)
            add(taskListPanel, BorderLayout.CENTER)

            headerLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    isExpanded = !isExpanded
                    updateDisplay()
                }
            })

            updateDisplay()
        }

        fun updateTasks(newTasks: List<TaskInfo>) {
            currentTasks = newTasks.toList()
            updateDisplay()
        }

        private fun getHeaderText(): String {
            val completed = currentTasks.count { it.status == ToolCallStatus.COMPLETED }
            val total = currentTasks.size
            val icon = if (isExpanded) "▼" else "▶"
            return "$icon 📋 Tasks ($completed/$total completed)"
        }

        private fun updateDisplay() {
            headerLabel.text = getHeaderText()
            taskListPanel.isVisible = isExpanded

            if (isExpanded) {
                taskListPanel.removeAll()
                for (task in currentTasks) {
                    val statusIcon = when (task.status) {
                        ToolCallStatus.COMPLETED -> "✓"
                        ToolCallStatus.FAILED -> "✗"
                        ToolCallStatus.IN_PROGRESS -> "▶"
                        else -> "○"
                    }
                    val statusColor = when (task.status) {
                        ToolCallStatus.COMPLETED -> JBColor(Color(0x2E7D32), Color(0x81C784))
                        ToolCallStatus.FAILED -> JBColor.RED
                        else -> accentColor
                    }
                    val taskLabel = JBLabel("$statusIcon ${task.title}").apply {
                        foreground = statusColor
                        font = font.deriveFont(font.size2D - 1)
                        border = JBUI.Borders.empty(1, 0)
                    }
                    taskListPanel.add(taskLabel)
                }
            }

            revalidate()
            repaint()
        }
    }
}

/**
 * Task information for tracking.
 */
data class TaskInfo(
    val id: String,
    val title: String,
    val status: ToolCallStatus,
    val output: String? = null
)

