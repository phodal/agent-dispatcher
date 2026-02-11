package com.github.phodal.acpmanager.dispatcher.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.phodal.routa.core.model.VerificationVerdict
import com.phodal.routa.core.provider.StreamChunk
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * GATE (Verifier) section panel — the bottom section of the DAG UI.
 *
 * Shows:
 * - Header: "GATE" label + verdict indicator
 * - Collapsible streaming area: verification output in real-time
 * - Verdict badge: APPROVED / NOT APPROVED
 *
 * Compact by default, expandable when verification is active.
 */
class GateSectionPanel : JPanel(BorderLayout()) {

    companion object {
        val GATE_ACCENT = JBColor(0xA78BFA, 0xA78BFA)
        val APPROVED_COLOR = JBColor(0x10B981, 0x10B981)
        val NOT_APPROVED_COLOR = JBColor(0xEF4444, 0xEF4444)
        val BLOCKED_COLOR = JBColor(0xF59E0B, 0xF59E0B)
        val INACTIVE_COLOR = JBColor(0x6B7280, 0x6B7280)
    }

    private val statusLabel = JBLabel("INACTIVE").apply {
        foreground = INACTIVE_COLOR
        font = font.deriveFont(Font.BOLD, 10f)
    }

    private val verdictBadge = JPanel(FlowLayout(FlowLayout.CENTER, 4, 1)).apply {
        isOpaque = true
        background = JBColor(0x21262D, 0x21262D)
        border = JBUI.Borders.empty(1, 8)
        isVisible = false

        add(JBLabel("—").apply {
            foreground = JBColor(0xC9D1D9, 0xC9D1D9)
            font = font.deriveFont(Font.BOLD, 10f)
            name = "verdictText"
        })
    }

    private val previewLabel = JBLabel("").apply {
        foreground = JBColor(0x8B949E, 0x8B949E)
        font = Font("Monospaced", Font.PLAIN, 10)
    }

    // Streaming output area
    private val outputArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBColor(0x0D1117, 0x0D1117)
        foreground = JBColor(0xC0C0C0, 0xA0A0A0)
        font = Font("Monospaced", Font.PLAIN, 11)
        border = JBUI.Borders.empty(4)
        rows = 4
    }

    private val outputScroll = JScrollPane(outputArea).apply {
        border = BorderFactory.createLineBorder(JBColor(0x21262D, 0x21262D))
        preferredSize = Dimension(0, 80)
        isVisible = false
    }

    private var expanded = false
    private val toggleLabel = JBLabel("▶ GATE").apply {
        foreground = GATE_ACCENT
        font = font.deriveFont(Font.BOLD, 11f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    // DAG connector at top
    private val dagConnector = object : JPanel() {
        init {
            isOpaque = false
            preferredSize = Dimension(0, 12)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor(0x30363D, 0x30363D)
            val cx = width / 2
            g2.drawLine(cx, 0, cx, height - 4)
            // Arrow head
            g2.drawLine(cx - 3, height - 7, cx, height - 4)
            g2.drawLine(cx + 3, height - 7, cx, height - 4)
        }
    }

    init {
        isOpaque = true
        background = JBColor(0x0D1117, 0x0D1117)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineBottom(JBColor(0x21262D, 0x21262D)),
            JBUI.Borders.empty(4, 12)
        )
        // Set compact size when collapsed (before DAG reaches GATE)
        minimumSize = Dimension(0, 40)
        preferredSize = Dimension(0, 50)

        // Header row
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false

            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(JBLabel(AllIcons.Actions.Checked).apply {
                    toolTipText = "GATE Verifier"
                })
                add(toggleLabel)
                add(JBLabel("│").apply { foreground = JBColor(0x30363D, 0x30363D) })
                add(previewLabel)
            }
            add(leftPanel, BorderLayout.WEST)

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                isOpaque = false
                add(verdictBadge)
                add(statusLabel)
            }
            add(rightPanel, BorderLayout.EAST)
        }

        // Center: streaming output
        val centerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(4)
            add(outputScroll, BorderLayout.CENTER)
        }

        add(dagConnector, BorderLayout.NORTH)
        add(headerPanel, BorderLayout.CENTER)
        add(centerPanel, BorderLayout.SOUTH)

        // Toggle
        toggleLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                expanded = !expanded
                outputScroll.isVisible = expanded
                toggleLabel.text = if (expanded) "▼ GATE" else "▶ GATE"
                revalidate()
                repaint()
            }
        })
    }

    /**
     * Update the gate status (active/inactive).
     */
    fun updateStatus(active: Boolean) {
        SwingUtilities.invokeLater {
            if (active) {
                statusLabel.text = "VERIFYING"
                statusLabel.foreground = GATE_ACCENT
                // Expand when active
                preferredSize = Dimension(0, 150)
            } else {
                statusLabel.text = "INACTIVE"
                statusLabel.foreground = INACTIVE_COLOR
                // Stay compact when inactive
                if (!expanded) {
                    preferredSize = Dimension(0, 50)
                }
            }
            revalidate()
        }
    }

    /**
     * Set the verification verdict.
     */
    fun setVerdict(verdict: VerificationVerdict?) {
        SwingUtilities.invokeLater {
            if (verdict == null) {
                verdictBadge.isVisible = false
                return@invokeLater
            }

            verdictBadge.isVisible = true
            val verdictLabel = verdictBadge.components.filterIsInstance<JBLabel>().firstOrNull()

            when (verdict) {
                VerificationVerdict.APPROVED -> {
                    verdictLabel?.text = "✅ APPROVED"
                    verdictLabel?.foreground = APPROVED_COLOR
                    verdictBadge.background = JBColor(0x0D2818, 0x0D2818)
                }

                VerificationVerdict.NOT_APPROVED -> {
                    verdictLabel?.text = "❌ NOT APPROVED"
                    verdictLabel?.foreground = NOT_APPROVED_COLOR
                    verdictBadge.background = JBColor(0x2D0D0D, 0x2D0D0D)
                }

                VerificationVerdict.BLOCKED -> {
                    verdictLabel?.text = "⚠ BLOCKED"
                    verdictLabel?.foreground = BLOCKED_COLOR
                    verdictBadge.background = JBColor(0x2D2200, 0x2D2200)
                }
            }

            verdictBadge.revalidate()
            verdictBadge.repaint()
        }
    }

    /**
     * Append a streaming chunk to the verification output.
     */
    fun appendChunk(chunk: StreamChunk) {
        SwingUtilities.invokeLater {
            when (chunk) {
                is StreamChunk.Text -> {
                    outputArea.append(chunk.content)
                    updatePreview()
                    autoScroll()

                    // Try to detect verdict from text
                    detectVerdictFromText(chunk.content)
                }

                is StreamChunk.ToolCall -> {
                    outputArea.append("\n[${chunk.status}] ${chunk.name}")
                    if (chunk.result != null) {
                        outputArea.append(" → ${chunk.result!!.take(100)}")
                    }
                    outputArea.append("\n")
                    autoScroll()
                }

                is StreamChunk.Error -> {
                    outputArea.append("\n[ERROR] ${chunk.message}\n")
                    autoScroll()
                }

                is StreamChunk.Completed -> {
                    statusLabel.text = "DONE"
                    statusLabel.foreground = GATE_ACCENT
                }

                else -> {}
            }

            // Auto-expand when content arrives
            if (!expanded && outputArea.text.isNotBlank()) {
                expanded = true
                outputScroll.isVisible = true
                toggleLabel.text = "▼ GATE"
                revalidate()
                repaint()
            }
        }
    }

    /**
     * Clear all output and reset.
     */
    fun clear() {
        SwingUtilities.invokeLater {
            outputArea.text = ""
            previewLabel.text = ""
            verdictBadge.isVisible = false
            updateStatus(false)
        }
    }

    private fun updatePreview() {
        val fullText = outputArea.text
        val preview = fullText.replace('\n', ' ').trim().take(50)
        previewLabel.text = if (preview.length < fullText.length) "$preview…" else preview
    }

    private fun autoScroll() {
        outputArea.caretPosition = outputArea.document.length
    }

    /**
     * Try to detect verdict from streaming text output.
     */
    private fun detectVerdictFromText(text: String) {
        val upper = text.uppercase()
        when {
            upper.contains("NOT APPROVED") || upper.contains("NOT_APPROVED") -> {
                setVerdict(VerificationVerdict.NOT_APPROVED)
            }

            upper.contains("APPROVED") && !upper.contains("NOT") -> {
                setVerdict(VerificationVerdict.APPROVED)
            }
        }
    }
}
