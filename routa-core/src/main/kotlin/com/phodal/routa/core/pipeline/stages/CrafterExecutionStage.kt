package com.phodal.routa.core.pipeline.stages

import com.phodal.routa.core.coordinator.CoordinationPhase
import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.model.AgentStatus
import com.phodal.routa.core.model.CompletionReport
import com.phodal.routa.core.pipeline.PipelineContext
import com.phodal.routa.core.pipeline.PipelineStage
import com.phodal.routa.core.pipeline.StageResult
import com.phodal.routa.core.runner.OrchestratorPhase
import com.phodal.routa.core.runner.OrchestratorResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * **Stage 3: CRAFTER Execution** — Executes all ready tasks with CRAFTER agents.
 *
 * This stage:
 * 1. Asks the coordinator for the next wave of ready tasks
 * 2. Creates CRAFTER agents for each task
 * 3. Builds context (task definition + agent identity) for each CRAFTER
 * 4. Runs CRAFTERs (sequentially or in parallel based on [PipelineContext.parallelCrafters])
 * 5. Ensures completion reports are filed (via tool calls or text parsing fallback)
 * 6. Cleans up provider resources for completed agents
 *
 * ## Outputs (written to [PipelineContext])
 * - [PipelineContext.delegations] — list of (agentId, taskId) pairs executed
 *
 * ## Early termination
 * If no tasks are ready AND all tasks are completed, returns
 * [StageResult.SkipRemaining] with a success result (no verification needed).
 */
class CrafterExecutionStage : PipelineStage {

    override val name = "crafter-execution"
    override val description = "Executes ready tasks with CRAFTER agents"

    override suspend fun execute(context: PipelineContext): StageResult {
        context.waveNumber = context.waveNumber + 1
        context.emitPhase(OrchestratorPhase.WaveStarting(context.waveNumber))

        // Ask the coordinator for the next wave of tasks
        val delegations = context.system.coordinator.executeNextWave()

        if (delegations.isEmpty()) {
            // No tasks to execute — check if everything is already done
            val phase = context.system.coordinator.coordinationState.value.phase
            if (phase == CoordinationPhase.COMPLETED) {
                context.emitPhase(OrchestratorPhase.Completed)
                return StageResult.SkipRemaining(buildSuccessResult(context))
            }
            // Nothing to do but not completed — continue to next stage
            return StageResult.Continue
        }

        context.delegations = delegations

        // Run CRAFTERs — parallel or sequential
        if (context.parallelCrafters && delegations.size > 1) {
            coroutineScope {
                val jobs = delegations.map { (crafterId, taskId) ->
                    async { runSingleCrafter(context, crafterId, taskId) }
                }
                jobs.awaitAll()
            }
        } else {
            for ((crafterId, taskId) in delegations) {
                runSingleCrafter(context, crafterId, taskId)
            }
        }

        return StageResult.Continue
    }

    // ── Single CRAFTER execution ────────────────────────────────────────

    /**
     * Run a single CRAFTER agent with streaming and report handling.
     */
    private suspend fun runSingleCrafter(
        context: PipelineContext,
        crafterId: String,
        taskId: String,
    ) {
        context.emitPhase(OrchestratorPhase.CrafterRunning(crafterId, taskId))

        val taskContext = context.system.coordinator.buildAgentContext(crafterId)
            ?: return // No task assigned
        val prompt = injectAgentIdentity(taskContext, crafterId, taskId)

        val crafterOutput = context.provider.runStreaming(
            AgentRole.CRAFTER, crafterId, prompt
        ) { chunk ->
            context.onStreamChunk?.invoke(crafterId, chunk)
        }

        // Ensure the CRAFTER's work is reported
        ensureCrafterReport(context, crafterId, taskId, crafterOutput)

        // Clean up provider resources
        context.provider.cleanup(crafterId)

        context.emitPhase(OrchestratorPhase.CrafterCompleted(crafterId, taskId))
    }

    // ── Report handling ─────────────────────────────────────────────────

    /**
     * If the CRAFTER didn't call `report_to_parent` via tool call,
     * parse the text output and file the report on their behalf.
     */
    private suspend fun ensureCrafterReport(
        context: PipelineContext,
        crafterId: String,
        taskId: String,
        output: String,
    ) {
        val agent = context.system.context.agentStore.get(crafterId) ?: return

        // If agent is already COMPLETED, Koog tool calling handled it
        if (agent.status == AgentStatus.COMPLETED) return

        // Agent didn't call report_to_parent — parse text and report for them
        val report = context.reportParser.parseCrafterReport(crafterId, taskId, output)
        context.system.tools.reportToParent(crafterId, report)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Inject the agent's identity into the prompt so the LLM knows
     * what values to pass when calling tools like `report_to_parent`.
     */
    private fun injectAgentIdentity(prompt: String, agentId: String, taskId: String): String {
        return buildString {
            appendLine("--- Agent Identity ---")
            appendLine("agent_id: $agentId")
            appendLine("task_id: $taskId")
            appendLine("----------------------")
            appendLine()
            append(prompt)
        }
    }

    private suspend fun buildSuccessResult(context: PipelineContext): OrchestratorResult.Success {
        val summary = context.system.coordinator.getTaskSummary()
        return OrchestratorResult.Success(summary)
    }
}
