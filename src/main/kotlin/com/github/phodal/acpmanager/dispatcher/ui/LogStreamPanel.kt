package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.dispatcher.model.AgentLogEntry
import com.github.phodal.acpmanager.dispatcher.model.LogLevel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * Log stream panel — the bottom section of the multi-agent dispatcher UI.
 *
 * Shows:
 * - Real-time log entries from all running tasks
 * - Filter by task / agent with tabs for parallel task logs
 * - Color-coded log levels (INF, WRN, ERR)
 */
class LogStreamPanel : JPanel(BorderLayout()) {

    private val tabbedPane = JBTabbedPane(JTabbedPane.TOP)
    private val allLogsArea = createLogTextArea()
    private val taskLogAreas = mutableMapOf<String, JTextArea>()
    private val filterCombo = JComboBox<String>().apply {
        addItem("All")
        preferredSize = Dimension(120, 24)
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        isOpaque = true
        background = JBColor(0x0D1117, 0x0D1117)
        border = JBUI.Borders.empty(4, 16, 8, 16)

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(JBLabel("⟩ LOG STREAM").apply {
                    foreground = JBColor(0xF59E0B, 0xF59E0B)
                    font = font.deriveFont(Font.BOLD, 11f)
                })
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(filterCombo)
            }
            add(rightPanel, BorderLayout.EAST)
        }
        add(headerPanel, BorderLayout.NORTH)

        // Tabbed log panes
        tabbedPane.addTab("All", JBScrollPane(allLogsArea))
        add(tabbedPane, BorderLayout.CENTER)

        filterCombo.addActionListener {
            val selected = filterCombo.selectedItem as? String ?: return@addActionListener
            if (selected == "All") {
                tabbedPane.selectedIndex = 0
            } else {
                val idx = tabbedPane.indexOfTab(selected)
                if (idx >= 0) tabbedPane.selectedIndex = idx
            }
        }
    }

    /**
     * Append a log entry to the log stream.
     */
    fun appendLog(entry: AgentLogEntry) {
        val formattedLine = formatLogEntry(entry)

        // Append to "All" tab
        SwingUtilities.invokeLater {
            allLogsArea.append(formattedLine + "\n")
            allLogsArea.caretPosition = allLogsArea.document.length
        }

        // Append to task-specific tab if present
        val taskId = entry.taskId
        if (taskId != null) {
            SwingUtilities.invokeLater {
                val taskArea = taskLogAreas.getOrPut(taskId) {
                    val area = createLogTextArea()
                    tabbedPane.addTab(taskId, JBScrollPane(area))
                    if (filterCombo.getItemAt(filterCombo.itemCount - 1) != taskId) {
                        filterCombo.addItem(taskId)
                    }
                    area
                }
                taskArea.append(formattedLine + "\n")
                taskArea.caretPosition = taskArea.document.length
            }
        }
    }

    /**
     * Clear all logs.
     */
    fun clearLogs() {
        SwingUtilities.invokeLater {
            allLogsArea.text = ""
            taskLogAreas.values.forEach { it.text = "" }
            // Remove all task tabs, keep "All"
            while (tabbedPane.tabCount > 1) {
                tabbedPane.removeTabAt(tabbedPane.tabCount - 1)
            }
            taskLogAreas.clear()
            filterCombo.removeAllItems()
            filterCombo.addItem("All")
        }
    }

    private fun formatLogEntry(entry: AgentLogEntry): String {
        val time = dateFormat.format(Date(entry.timestamp))
        val level = entry.level.name.padEnd(3)
        val source = "[${entry.source}]"
        val taskPrefix = entry.taskId?.let { "[${it}]" } ?: ""
        return "$time  $level $source $taskPrefix ${entry.message}"
    }

    private fun createLogTextArea(): JTextArea {
        return JTextArea().apply {
            isEditable = false
            lineWrap = false
            font = Font("Monospaced", Font.PLAIN, 11)
            background = JBColor(0x0D1117, 0x0D1117)
            foreground = JBColor(0x8B949E, 0x8B949E)
            border = JBUI.Borders.empty(4)
        }
    }
}
