package com.phodal.routa.core.pipeline.stages

import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.pipeline.PipelineContext
import com.phodal.routa.core.pipeline.PipelineStage
import com.phodal.routa.core.pipeline.StageResult
import com.phodal.routa.core.runner.OrchestratorPhase

/**
 * **Stage 1: Planning** — ROUTA analyzes the user request and generates a plan.
 *
 * This stage:
 * 1. Initializes the coordinator (creates the ROUTA agent)
 * 2. Sends the user request to the ROUTA agent via the provider
 * 3. Collects the plan output (which should contain `@@@task` blocks)
 * 4. Writes the plan output and ROUTA agent ID to the pipeline context
 *
 * ## Outputs (written to [PipelineContext])
 * - [PipelineContext.routaAgentId] — the ROUTA agent's ID
 * - [PipelineContext.planOutput] — the raw plan text from the LLM
 *
 * ## Streaming
 * If the provider supports streaming, plan chunks are forwarded to the
 * UI via [PipelineContext.onStreamChunk].
 */
class PlanningStage : PipelineStage {

    override val name = "planning"
    override val description = "ROUTA analyzes the user request and generates a task plan"

    override suspend fun execute(context: PipelineContext): StageResult {
        context.emitPhase(OrchestratorPhase.Planning)

        // Initialize the coordinator — creates the ROUTA agent
        val routaAgentId = context.system.coordinator.initialize(context.workspaceId)
        context.routaAgentId = routaAgentId

        // Build the planning prompt
        val planPrompt = buildPlanPrompt(context.userRequest)

        // Execute ROUTA via streaming provider
        val planOutput = context.provider.runStreaming(
            AgentRole.ROUTA, routaAgentId, planPrompt
        ) { chunk ->
            context.onStreamChunk?.invoke(routaAgentId, chunk)
        }

        context.planOutput = planOutput
        context.emitPhase(OrchestratorPhase.PlanReady(planOutput))

        return StageResult.Continue
    }

    private fun buildPlanPrompt(userRequest: String): String = buildString {
        appendLine("## User Request")
        appendLine()
        appendLine(userRequest)
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("Please analyze this request and break it down into @@@task blocks.")
        appendLine("Each task should have: Title, Objective, Scope, Definition of Done, and Verification commands.")
        appendLine("Use the @@@task ... @@@ format as specified in your instructions.")
    }
}
