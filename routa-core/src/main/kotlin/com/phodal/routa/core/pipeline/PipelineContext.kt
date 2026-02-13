package com.phodal.routa.core.pipeline

import com.phodal.routa.core.RoutaSystem
import com.phodal.routa.core.provider.AgentProvider
import com.phodal.routa.core.provider.StreamChunk
import com.phodal.routa.core.report.ReportParser
import com.phodal.routa.core.runner.OrchestratorPhase

/**
 * Shared context flowing through all [PipelineStage]s in a pipeline execution.
 *
 * This is the communication channel between stages — each stage reads inputs
 * from previous stages and writes outputs for downstream stages via [metadata].
 *
 * ## Key design decisions:
 *
 * 1. **Immutable configuration + mutable metadata**: The system, provider, and
 *    workspace are fixed for a pipeline run. Stages communicate through the
 *    mutable [metadata] map for inter-stage data passing.
 *
 * 2. **Callback-based observation**: Phase changes and streaming chunks are
 *    forwarded via callbacks, keeping the pipeline decoupled from UI.
 *
 * 3. **ReportParser is pluggable**: Different report parsing strategies can
 *    be injected for different LLM output formats.
 *
 * ## Well-Known Metadata Keys
 *
 * Stages use these keys to pass data between each other:
 * - [KEY_PLAN_OUTPUT] — The raw text output from the planning stage.
 * - [KEY_TASK_IDS] — List of task IDs created by the registration stage.
 * - [KEY_ROUTA_AGENT_ID] — The Routa coordinator agent's ID.
 * - [KEY_WAVE_NUMBER] — Current wave/iteration number.
 * - [KEY_DELEGATIONS] — List of (agentId, taskId) pairs from crafter execution.
 */
class PipelineContext(
    /** The Routa multi-agent system (stores, coordinator, tools, event bus). */
    val system: RoutaSystem,

    /** The agent execution provider (ACP, Koog, Claude, etc.). */
    val provider: AgentProvider,

    /** The workspace identifier for this pipeline run. */
    val workspaceId: String,

    /** The original user request that triggered this pipeline. */
    val userRequest: String,

    /** Strategy for parsing LLM text output into structured reports. */
    val reportParser: ReportParser,

    /** Whether to execute CRAFTERs in parallel within a wave. */
    val parallelCrafters: Boolean = false,

    /** Callback invoked when the orchestration phase changes. */
    val onPhaseChange: (suspend (OrchestratorPhase) -> Unit)? = null,

    /** Callback invoked when an agent produces a streaming chunk. */
    val onStreamChunk: ((agentId: String, chunk: StreamChunk) -> Unit)? = null,
) {

    /**
     * Mutable metadata map for inter-stage communication.
     *
     * Stages write their outputs here and downstream stages read them.
     * Use the companion object constants as keys for type-safe access.
     */
    val metadata: MutableMap<String, Any> = mutableMapOf()

    // ── Typed accessors for well-known metadata ──────────────────────────

    /** The raw plan output from the planning stage. */
    var planOutput: String
        get() = metadata[KEY_PLAN_OUTPUT] as? String ?: ""
        set(value) { metadata[KEY_PLAN_OUTPUT] = value }

    /** The list of task IDs created by the registration stage. */
    var taskIds: List<String>
        get() {
            @Suppress("UNCHECKED_CAST")
            return metadata[KEY_TASK_IDS] as? List<String> ?: emptyList()
        }
        set(value) { metadata[KEY_TASK_IDS] = value }

    /** The Routa coordinator agent's ID. */
    var routaAgentId: String
        get() = metadata[KEY_ROUTA_AGENT_ID] as? String ?: ""
        set(value) { metadata[KEY_ROUTA_AGENT_ID] = value }

    /** The current wave/iteration number. */
    var waveNumber: Int
        get() = metadata[KEY_WAVE_NUMBER] as? Int ?: 0
        set(value) { metadata[KEY_WAVE_NUMBER] = value }

    /** The delegations (agentId → taskId) from the most recent crafter execution. */
    var delegations: List<Pair<String, String>>
        get() {
            @Suppress("UNCHECKED_CAST")
            return metadata[KEY_DELEGATIONS] as? List<Pair<String, String>> ?: emptyList()
        }
        set(value) { metadata[KEY_DELEGATIONS] = value }

    // ── Helper methods ──────────────────────────────────────────────────

    /** Emit a phase change event. */
    suspend fun emitPhase(phase: OrchestratorPhase) {
        onPhaseChange?.invoke(phase)
    }

    companion object {
        const val KEY_PLAN_OUTPUT = "planOutput"
        const val KEY_TASK_IDS = "taskIds"
        const val KEY_ROUTA_AGENT_ID = "routaAgentId"
        const val KEY_WAVE_NUMBER = "waveNumber"
        const val KEY_DELEGATIONS = "delegations"
    }
}
