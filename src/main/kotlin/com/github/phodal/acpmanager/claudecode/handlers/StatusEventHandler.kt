package com.github.phodal.acpmanager.claudecode.handlers

import com.github.phodal.acpmanager.claudecode.context.RenderContext
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.reflect.KClass

/**
 * Handler for status events (Info, Error, Connected, Disconnected, PromptComplete, Clear).
 */
class StatusEventHandler : MultiEventHandler() {

    override val supportedEvents: Set<KClass<out RenderEvent>> = setOf(
        RenderEvent.Info::class,
        RenderEvent.Error::class,
        RenderEvent.Connected::class,
        RenderEvent.Disconnected::class,
        RenderEvent.PromptComplete::class,
        RenderEvent.ModeChange::class,
        RenderEvent.PlanUpdate::class,
        RenderEvent.Clear::class
    )

    override fun handle(event: RenderEvent, context: RenderContext) {
        when (event) {
            is RenderEvent.Info -> handleInfo(event, context)
            is RenderEvent.Error -> handleError(event, context)
            is RenderEvent.Connected -> { /* Ignore - don't show connection message */ }
            is RenderEvent.Disconnected -> handleDisconnected(context)
            is RenderEvent.PromptComplete -> { /* Ignore */ }
            is RenderEvent.ModeChange -> { /* Ignore */ }
            is RenderEvent.PlanUpdate -> { /* Plan updates handled separately */ }
            is RenderEvent.Clear -> context.clear()
            else -> {}
        }
    }

    private fun handleInfo(event: RenderEvent.Info, context: RenderContext) {
        val label = JBLabel("ℹ ${event.message}").apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size2D - 1)
            border = JBUI.Borders.empty(2, 8)
        }
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(label, BorderLayout.WEST)
        }
        context.addPanel(panel)
    }

    private fun handleError(event: RenderEvent.Error, context: RenderContext) {
        val label = JBLabel("⚠️ ${event.message}").apply {
            foreground = JBColor.RED
            border = JBUI.Borders.empty(2, 8)
        }
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(label, BorderLayout.WEST)
        }
        context.addPanel(panel)
    }

    private fun handleDisconnected(context: RenderContext) {
        handleInfo(RenderEvent.Info("Disconnected"), context)
    }
}

