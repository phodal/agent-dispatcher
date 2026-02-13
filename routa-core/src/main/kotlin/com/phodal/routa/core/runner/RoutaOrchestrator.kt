package com.phodal.routa.core.runner

import com.phodal.routa.core.RoutaSystem
import com.phodal.routa.core.pipeline.OrchestrationPipeline
import com.phodal.routa.core.pipeline.PipelineContext
import com.phodal.routa.core.provider.AgentProvider
import com.phodal.routa.core.provider.AgentRunnerAdapter
import com.phodal.routa.core.provider.StreamChunk
import com.phodal.routa.core.report.ReportParser
import com.phodal.routa.core.report.TextPatternReportParser

/**
 * The multi-agent orchestration entry point.
 *
 * Delegates to a composable [OrchestrationPipeline] that executes
 * independent, testable stages in sequence. The default pipeline
 * implements the full ROUTA → CRAFTER → GATE workflow:
 *
 * ```
 * User Request
 *   → [PlanningStage] ROUTA plans (@@@task blocks)
 *     → [TaskRegistrationStage] Parse and register tasks
 *       → [CrafterExecutionStage] CRAFTER agents execute tasks
 *         → [GateVerificationStage] GATE verifies work
 *           → APPROVED: done
 *           → NOT APPROVED: repeat from CrafterExecution
 * ```
 *
 * ## Customizing the Pipeline
 *
 * ```kotlin
 * // Use the default pipeline
 * val orchestrator = RoutaOrchestrator(system, provider, "workspace")
 *
 * // Use a custom pipeline (e.g., skip verification)
 * val orchestrator = RoutaOrchestrator(
 *     system, provider, "workspace",
 *     pipeline = OrchestrationPipeline.withoutVerification(),
 * )
 *
 * // Add custom stages
 * val orchestrator = RoutaOrchestrator(
 *     system, provider, "workspace",
 *     pipeline = OrchestrationPipeline.default()
 *         .withStageAt(3, CodeReviewStage()),
 * )
 * ```
 *
 * ## Backward Compatibility
 *
 * This class maintains the same public API as before. The [AgentRunner]
 * parameter is accepted but must be an [AgentProvider] instance.
 * All streaming, phase callbacks, and result types are unchanged.
 *
 * @param routa The Routa multi-agent system.
 * @param runner The agent execution provider (must implement [AgentProvider]).
 * @param workspaceId The workspace to operate in.
 * @param maxWaves Maximum pipeline iterations (default: 3). Overridden by pipeline config if provided.
 * @param parallelCrafters Whether CRAFTERs execute in parallel within a wave.
 * @param onPhaseChange Callback for orchestration phase changes.
 * @param onStreamChunk Callback for agent streaming chunks.
 * @param pipeline Custom pipeline. If null, uses [OrchestrationPipeline.default].
 * @param reportParser Custom report parser. If null, uses [TextPatternReportParser].
 */
class RoutaOrchestrator(
    private val routa: RoutaSystem,
    private val runner: AgentRunner,
    private val workspaceId: String,
    private val maxWaves: Int = 3,
    private val parallelCrafters: Boolean = false,
    private val onPhaseChange: (suspend (OrchestratorPhase) -> Unit)? = null,
    private val onStreamChunk: ((agentId: String, chunk: StreamChunk) -> Unit)? = null,
    private val pipeline: OrchestrationPipeline? = null,
    private val reportParser: ReportParser? = null,
) {

    // Wrap legacy AgentRunner in an adapter if needed
    private val provider: AgentProvider = when (runner) {
        is AgentProvider -> runner
        else -> AgentRunnerAdapter(runner)
    }

    /**
     * The resolved pipeline — either the one passed in, or the default.
     */
    val resolvedPipeline: OrchestrationPipeline =
        pipeline ?: OrchestrationPipeline.default(maxWaves)

    /**
     * The resolved report parser — either the one passed in, or the default.
     */
    val resolvedReportParser: ReportParser =
        reportParser ?: TextPatternReportParser()

    /**
     * Execute the full multi-agent orchestration flow.
     *
     * Creates a [PipelineContext] and delegates execution to the [OrchestrationPipeline].
     *
     * @param userRequest The user's requirement/task description.
     * @return The orchestration result.
     */
    suspend fun execute(userRequest: String): OrchestratorResult {
        val context = PipelineContext(
            system = routa,
            provider = provider,
            workspaceId = workspaceId,
            userRequest = userRequest,
            reportParser = resolvedReportParser,
            parallelCrafters = parallelCrafters,
            onPhaseChange = onPhaseChange,
            onStreamChunk = onStreamChunk,
        )

        return resolvedPipeline.execute(context)
    }
}

// ── Orchestrator phases (for UI/CLI callbacks) ──────────────────────────

/**
 * Phases emitted during orchestration for UI/CLI observation.
 *
 * These phases are emitted by pipeline stages via [PipelineContext.emitPhase]
 * and forwarded to the caller via the [RoutaOrchestrator.onPhaseChange] callback.
 */
sealed class OrchestratorPhase {
    /** Pipeline is initializing. */
    data object Initializing : OrchestratorPhase()

    /** ROUTA is planning tasks. */
    data object Planning : OrchestratorPhase()

    /** ROUTA's plan output is ready. */
    data class PlanReady(val planOutput: String) : OrchestratorPhase()

    /** Tasks have been parsed and registered. */
    data class TasksRegistered(val count: Int) : OrchestratorPhase()

    /** A new wave of CRAFTER execution is starting. */
    data class WaveStarting(val wave: Int) : OrchestratorPhase()

    /** A specific CRAFTER is running. */
    data class CrafterRunning(val crafterId: String, val taskId: String) : OrchestratorPhase()

    /** A specific CRAFTER completed. */
    data class CrafterCompleted(val crafterId: String, val taskId: String) : OrchestratorPhase()

    /** GATE verification is starting. */
    data class VerificationStarting(val wave: Int) : OrchestratorPhase()

    /** GATE verification completed. */
    data class VerificationCompleted(val gateId: String, val output: String) : OrchestratorPhase()

    /** GATE found issues, tasks need fixes. */
    data class NeedsFix(val wave: Int) : OrchestratorPhase()

    /** All tasks completed successfully. */
    data object Completed : OrchestratorPhase()

    /** Maximum iterations reached without full approval. */
    data class MaxWavesReached(val waves: Int) : OrchestratorPhase()
}

// ── Orchestrator results ────────────────────────────────────────────────

/**
 * The final outcome of an orchestration run.
 */
sealed class OrchestratorResult {
    /** All tasks completed and verified successfully. */
    data class Success(
        val taskSummaries: List<com.phodal.routa.core.coordinator.TaskSummary>,
    ) : OrchestratorResult()

    /** ROUTA's plan contained no @@@task blocks. */
    data class NoTasks(val planOutput: String) : OrchestratorResult()

    /** Maximum iterations exhausted without full approval. */
    data class MaxWavesReached(
        val waves: Int,
        val taskSummaries: List<com.phodal.routa.core.coordinator.TaskSummary>,
    ) : OrchestratorResult()

    /** Pipeline failed with an error. */
    data class Failed(val error: String) : OrchestratorResult()
}
