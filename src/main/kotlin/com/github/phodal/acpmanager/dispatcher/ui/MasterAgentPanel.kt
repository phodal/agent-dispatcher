package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.dispatcher.model.*
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Master Agent section — the top panel in the multi-agent dispatcher UI.
 *
 * Shows:
 * - Master Agent selector (combo box to choose which ACP agent acts as planner)
 * - Status indicator (RUNNING, IDLE, etc.)
 * - Thinking/plan preview area
 */
class MasterAgentPanel : JPanel(BorderLayout()) {

    private val statusLabel = JBLabel("IDLE").apply {
        foreground = JBColor(0x6B7280, 0x9CA3AF)
        font = font.deriveFont(Font.BOLD, 11f)
    }

    private val statusDot = JBLabel("●").apply {
        foreground = JBColor(0x6B7280, 0x9CA3AF)
        font = font.deriveFont(12f)
    }

    private val masterAgentCombo = JComboBox<String>()
    private val thinkingArea = JTextArea(3, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBColor(0x1A1A2E, 0x0F0F1A)
        foreground = JBColor(0xC0C0C0, 0xA0A0A0)
        font = Font("Monospaced", Font.PLAIN, 12)
        border = JBUI.Borders.empty(8)
    }

    private val planItemsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    private val resultArea = JTextArea(6, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBColor(0x0D2818, 0x0D2818)
        foreground = JBColor(0xA7F3D0, 0xA7F3D0)
        font = Font("Monospaced", Font.PLAIN, 12)
        border = JBUI.Borders.empty(8)
    }

    private val resultPanel = JPanel(BorderLayout(0, 4)).apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(8)
        isVisible = false

        val resultLabel = JBLabel("OUTPUT").apply {
            foreground = JBColor(0x10B981, 0x10B981)
            font = font.deriveFont(Font.BOLD, 10f)
        }
        add(resultLabel, BorderLayout.NORTH)

        val resultScroll = JScrollPane(resultArea).apply {
            border = BorderFactory.createLineBorder(JBColor(0x10B981, 0x21262D))
            preferredSize = Dimension(0, 120)
        }
        add(resultScroll, BorderLayout.CENTER)
    }

    var onMasterAgentChanged: (String) -> Unit = {}

    init {
        isOpaque = true
        background = JBColor(0x0D1117, 0x0D1117)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineBottom(JBColor(0x21262D, 0x21262D)),
            JBUI.Borders.empty(12, 16)
        )

        // Header row: icon + title + status
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(JBLabel(AllIcons.Actions.Lightning).apply {
                    toolTipText = "Master Agent"
                })
                add(JBLabel("MASTER AGENT").apply {
                    foreground = JBColor(0x58A6FF, 0x58A6FF)
                    font = font.deriveFont(Font.BOLD, 13f)
                })
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(statusDot)
                add(statusLabel)
            }
            add(rightPanel, BorderLayout.EAST)
        }
        add(headerPanel, BorderLayout.NORTH)

        // Center: thinking label + agent selector
        val centerPanel = JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)

            // Thinking label
            val thinkingLabel = JBLabel("THINKING").apply {
                foreground = JBColor(0x6B7280, 0x6B7280)
                font = font.deriveFont(Font.BOLD, 10f)
            }
            add(thinkingLabel, BorderLayout.NORTH)

            // Thinking content
            val thinkingScroll = JScrollPane(thinkingArea).apply {
                border = BorderFactory.createLineBorder(JBColor(0x21262D, 0x21262D))
                preferredSize = Dimension(0, 60)
            }
            add(thinkingScroll, BorderLayout.CENTER)

            // Plan items + result area
            val bottomPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(JScrollPane(planItemsPanel).apply {
                    border = JBUI.Borders.emptyTop(4)
                    preferredSize = Dimension(0, 80)
                })
                add(resultPanel)
            }
            add(bottomPanel, BorderLayout.SOUTH)
        }
        add(centerPanel, BorderLayout.CENTER)

        // Bottom: agent selector
        val selectorPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)

            add(JBLabel("Agent:").apply {
                foreground = JBColor(0x8B949E, 0x8B949E)
            })
            masterAgentCombo.preferredSize = Dimension(200, 28)
            masterAgentCombo.addActionListener {
                val selected = masterAgentCombo.selectedItem as? String ?: return@addActionListener
                onMasterAgentChanged(selected)
            }
            add(masterAgentCombo)
        }
        add(selectorPanel, BorderLayout.SOUTH)
    }

    fun setAvailableAgents(agents: List<String>) {
        masterAgentCombo.removeAllItems()
        agents.forEach { masterAgentCombo.addItem(it) }
    }

    fun setSelectedAgent(agentKey: String) {
        masterAgentCombo.selectedItem = agentKey
    }

    fun updateStatus(status: DispatcherStatus) {
        statusLabel.text = status.name
        val color = when (status) {
            DispatcherStatus.IDLE -> JBColor(0x6B7280, 0x9CA3AF)
            DispatcherStatus.PLANNING -> JBColor(0xF59E0B, 0xF59E0B)
            DispatcherStatus.PLANNED -> JBColor(0x3B82F6, 0x3B82F6)
            DispatcherStatus.RUNNING -> JBColor(0x10B981, 0x10B981)
            DispatcherStatus.PAUSED -> JBColor(0xF59E0B, 0xF59E0B)
            DispatcherStatus.COMPLETED -> JBColor(0x10B981, 0x10B981)
            DispatcherStatus.FAILED -> JBColor(0xEF4444, 0xEF4444)
        }
        statusLabel.foreground = color
        statusDot.foreground = color
    }

    fun updateThinking(text: String) {
        thinkingArea.text = text
        thinkingArea.caretPosition = 0
    }

    fun updatePlanItems(items: List<PlanItemDisplay>) {
        planItemsPanel.removeAll()
        for (item in items) {
            planItemsPanel.add(createPlanItemRow(item))
        }
        planItemsPanel.revalidate()
        planItemsPanel.repaint()
    }

    fun updateFinalOutput(output: String?) {
        if (output.isNullOrBlank()) {
            resultPanel.isVisible = false
            resultArea.text = ""
        } else {
            resultArea.text = output
            resultArea.caretPosition = 0
            resultPanel.isVisible = true
        }
        revalidate()
        repaint()
    }

    private fun createPlanItemRow(item: PlanItemDisplay): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 0)

            val statusIcon = when (item.status) {
                "ACTIVE" -> JBLabel("●").apply { foreground = JBColor(0x10B981, 0x10B981) }
                "BLOCKED" -> JBLabel("●").apply { foreground = JBColor(0xEF4444, 0xEF4444) }
                "QUEUED" -> JBLabel("○").apply { foreground = JBColor(0x6B7280, 0x6B7280) }
                "DONE" -> JBLabel("✓").apply { foreground = JBColor(0x10B981, 0x10B981) }
                else -> JBLabel("○").apply { foreground = JBColor(0x6B7280, 0x6B7280) }
            }

            val labelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(JBLabel("${item.index}").apply {
                    foreground = JBColor(0x6B7280, 0x6B7280)
                    font = font.deriveFont(11f)
                })
                add(statusIcon)
                add(JBLabel(item.text).apply {
                    foreground = JBColor(0xC9D1D9, 0xC9D1D9)
                    font = font.deriveFont(12f)
                })
            }
            add(labelPanel, BorderLayout.CENTER)
        }
    }
}

/**
 * Display data for a plan item row.
 */
data class PlanItemDisplay(
    val index: String,
    val text: String,
    val status: String,
)
