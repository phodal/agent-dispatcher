package com.phodal.routa.core.pipeline.stages

import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.model.AgentStatus
import com.phodal.routa.core.model.CompletionReport
import com.phodal.routa.core.model.TaskStatus
import com.phodal.routa.core.model.VerificationVerdict
import com.phodal.routa.core.pipeline.PipelineContext
import com.phodal.routa.core.pipeline.PipelineStage
import com.phodal.routa.core.pipeline.StageResult
import com.phodal.routa.core.runner.OrchestratorPhase
import com.phodal.routa.core.runner.OrchestratorResult

/**
 * **Stage 4: GATE Verification** — Verifies completed work and decides next steps.
 *
 * This stage:
 * 1. Creates a GATE agent for tasks in REVIEW_REQUIRED status
 * 2. Builds verification context (task definitions, CRAFTER reports, acceptance criteria)
 * 3. Runs the GATE agent to verify all completed work
 * 4. Ensures the verdict is filed (via tool calls or text parsing fallback)
 * 5. Checks the outcome:
 *    - **All approved** → pipeline completes with [StageResult.Done]
 *    - **Some need fixes** → resets tasks and returns [StageResult.RepeatPipeline]
 *    - **No verification needed** → pipeline completes
 *
 * ## Decision flow
 * ```
 * GATE output
 *   → APPROVED for all tasks → Done(Success)
 *   → NOT APPROVED for some  → reset NEEDS_FIX tasks → RepeatPipeline
 * ```
 */
class GateVerificationStage : PipelineStage {

    override val name = "gate-verification"
    override val description = "GATE verifies completed work against acceptance criteria"

    override suspend fun execute(context: PipelineContext): StageResult {
        context.emitPhase(OrchestratorPhase.VerificationStarting(context.waveNumber))

        val gateAgentId = context.system.coordinator.startVerification()
        if (gateAgentId == null) {
            // No tasks need verification — we're done
            context.emitPhase(OrchestratorPhase.Completed)
            return StageResult.Done(buildSuccessResult(context))
        }

        // Build verification context
        val gateContext = buildGateContext(context, gateAgentId)
        val reviewTasks = context.system.context.taskStore.listByStatus(
            context.workspaceId, TaskStatus.REVIEW_REQUIRED
        )
        val taskIdsList = reviewTasks.joinToString(", ") { it.id }
        val gatePrompt = injectAgentIdentity(gateContext, gateAgentId, taskIdsList)

        // Run the GATE agent
        val gateOutput = context.provider.runStreaming(
            AgentRole.GATE, gateAgentId, gatePrompt
        ) { chunk ->
            context.onStreamChunk?.invoke(gateAgentId, chunk)
        }

        // Ensure the verdict is reported
        ensureGateReport(context, gateAgentId, gateOutput)

        // Clean up provider resources
        context.provider.cleanup(gateAgentId)

        context.emitPhase(OrchestratorPhase.VerificationCompleted(gateAgentId, gateOutput))

        // ── Decide next step based on task statuses ─────────────────────

        val allTasks = context.system.context.taskStore.listByWorkspace(context.workspaceId)
        val needsFixTasks = allTasks.filter { it.status == TaskStatus.NEEDS_FIX }

        return when {
            // All tasks completed — success!
            allTasks.isNotEmpty() && allTasks.all { it.status == TaskStatus.COMPLETED } -> {
                context.emitPhase(OrchestratorPhase.Completed)
                StageResult.Done(buildSuccessResult(context))
            }

            // Some tasks need fixes — reset and repeat
            needsFixTasks.isNotEmpty() -> {
                context.emitPhase(OrchestratorPhase.NeedsFix(context.waveNumber))
                resetNeedsFixTasks(context)
                StageResult.RepeatPipeline
            }

            // Other states — continue (might have more pending tasks)
            else -> StageResult.Continue
        }
    }

    // ── Gate report handling ─────────────────────────────────────────────

    /**
     * If the GATE didn't call `report_to_parent` via tool call,
     * parse the verdict from text and file reports on their behalf.
     */
    private suspend fun ensureGateReport(
        context: PipelineContext,
        gateAgentId: String,
        output: String,
    ) {
        val agent = context.system.context.agentStore.get(gateAgentId) ?: return

        // If agent is already COMPLETED, Koog tool calling handled it
        if (agent.status == AgentStatus.COMPLETED) return

        // Parse verdicts from text output
        val reviewTasks = context.system.context.taskStore.listByStatus(
            context.workspaceId, TaskStatus.REVIEW_REQUIRED
        )
        val verdicts = context.reportParser.parseGateVerdicts(gateAgentId, output, reviewTasks)

        // File a report for each task
        for (task in reviewTasks) {
            val verdict = verdicts[task.id]
            val report = CompletionReport(
                agentId = gateAgentId,
                taskId = task.id,
                summary = verdict?.summary ?: "No verdict parsed",
                success = verdict?.verdict == VerificationVerdict.APPROVED,
            )
            context.system.tools.reportToParent(gateAgentId, report)
        }
    }

    // ── Context building ─────────────────────────────────────────────────

    /**
     * Build the verification context for the GATE agent.
     */
    private suspend fun buildGateContext(
        context: PipelineContext,
        gateAgentId: String,
    ): String {
        val roleContext = context.system.coordinator.buildAgentContext(gateAgentId) ?: ""
        val tasks = context.system.context.taskStore.listByStatus(
            context.workspaceId, TaskStatus.REVIEW_REQUIRED
        )

        return buildString {
            appendLine(roleContext)
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("# Tasks to Verify")
            appendLine()

            for (task in tasks) {
                appendLine("## ${task.title}")
                appendLine()
                appendLine("**Task ID:** ${task.id}")
                appendLine()
                appendLine("**Objective:** ${task.objective}")
                appendLine()

                if (task.acceptanceCriteria.isNotEmpty()) {
                    appendLine("**Acceptance Criteria:**")
                    task.acceptanceCriteria.forEach { appendLine("- $it") }
                    appendLine()
                }

                if (task.completionSummary != null) {
                    appendLine("**Crafter Report:** ${task.completionSummary}")
                    appendLine()
                }

                // Include CRAFTER conversation for evidence
                val crafterId = task.assignedTo
                if (crafterId != null) {
                    val conversation = context.system.context.conversationStore.getLastN(crafterId, 5)
                    if (conversation.isNotEmpty()) {
                        appendLine("**Crafter Conversation (last ${conversation.size} messages):**")
                        for (msg in conversation) {
                            appendLine("> [${msg.role}]: ${msg.content.take(500)}")
                        }
                        appendLine()
                    }
                }

                if (task.verificationCommands.isNotEmpty()) {
                    appendLine("**Verification Commands:**")
                    task.verificationCommands.forEach { appendLine("- `$it`") }
                    appendLine()
                }

                appendLine("---")
            }

            appendLine()
            appendLine("Please verify each task against its Acceptance Criteria.")
            appendLine("Output your verdict: ✅ APPROVED or ❌ NOT APPROVED, with evidence.")
        }
    }

    // ── Task reset ───────────────────────────────────────────────────────

    /**
     * Reset NEEDS_FIX tasks back to PENDING for the next wave.
     */
    private suspend fun resetNeedsFixTasks(context: PipelineContext) {
        val tasks = context.system.context.taskStore.listByStatus(
            context.workspaceId, TaskStatus.NEEDS_FIX
        )
        for (task in tasks) {
            context.system.context.taskStore.save(
                task.copy(
                    status = TaskStatus.PENDING,
                    assignedTo = null,
                    updatedAt = java.time.Instant.now().toString(),
                )
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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
