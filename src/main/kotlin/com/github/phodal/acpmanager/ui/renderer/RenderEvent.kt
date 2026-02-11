package com.github.phodal.acpmanager.ui.renderer

import com.agentclientprotocol.model.ToolCallStatus
import com.github.phodal.acpmanager.acp.MessageReference

/**
 * Render events that represent UI updates from ACP events.
 * These events are consumed by AcpEventRenderer implementations to update the UI.
 */
sealed class RenderEvent {
    abstract val timestamp: Long

    /**
     * User sent a message.
     */
    data class UserMessage(
        val content: String,
        val references: List<MessageReference> = emptyList(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Agent started thinking (extended thinking/reasoning).
     */
    data class ThinkingStart(
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Agent thinking content chunk.
     */
    data class ThinkingChunk(
        val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Agent finished thinking.
     */
    data class ThinkingEnd(
        val fullContent: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Agent message streaming started.
     */
    data class MessageStart(
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Agent message content chunk.
     */
    data class MessageChunk(
        val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Agent message streaming ended.
     */
    data class MessageEnd(
        val fullContent: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Tool call started.
     */
    data class ToolCallStart(
        val toolCallId: String,
        val toolName: String,
        val title: String?,
        val kind: String?,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Tool call status updated.
     */
    data class ToolCallUpdate(
        val toolCallId: String,
        val status: ToolCallStatus,
        val title: String?,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Tool call completed with result.
     */
    data class ToolCallEnd(
        val toolCallId: String,
        val status: ToolCallStatus,
        val title: String?,
        val output: String?,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Tool call parameters streaming update.
     */
    data class ToolCallParameterUpdate(
        val toolCallId: String,
        val partialParameters: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Thinking content signature for verification.
     */
    data class ThinkingSignature(
        val signature: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Plan update from agent.
     */
    data class PlanUpdate(
        val entries: List<PlanEntry>,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Mode changed.
     */
    data class ModeChange(
        val modeId: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Informational message.
     */
    data class Info(
        val message: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Error message.
     */
    data class Error(
        val message: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Session connected.
     */
    data class Connected(
        val agentKey: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Session disconnected.
     */
    data class Disconnected(
        val agentKey: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Clear all rendered content (for new session).
     */
    data class Clear(
        val agentKey: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Prompt completed.
     */
    data class PromptComplete(
        val stopReason: String?,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()

    /**
     * Task status update - emitted for UI components outside the renderer
     * (e.g., task status panel above input area).
     */
    data class TaskUpdate(
        val tasks: List<TaskItem>,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RenderEvent()
}

/**
 * A task item for TaskUpdate event.
 */
data class TaskItem(
    val id: String,
    val title: String,
    val status: ToolCallStatus,
)

/**
 * A plan entry for PlanUpdate event.
 */
data class PlanEntry(
    val content: String,
    val status: PlanEntryStatus,
)

enum class PlanEntryStatus {
    PENDING, IN_PROGRESS, COMPLETED
}

