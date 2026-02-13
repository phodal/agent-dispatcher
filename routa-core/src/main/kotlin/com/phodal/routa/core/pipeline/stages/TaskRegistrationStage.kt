package com.phodal.routa.core.pipeline.stages

import com.phodal.routa.core.pipeline.PipelineContext
import com.phodal.routa.core.pipeline.PipelineStage
import com.phodal.routa.core.pipeline.StageResult
import com.phodal.routa.core.runner.OrchestratorPhase
import com.phodal.routa.core.runner.OrchestratorResult

/**
 * **Stage 2: Task Registration** — Parses the plan output and registers tasks.
 *
 * This stage:
 * 1. Reads the plan output from [PipelineContext.planOutput]
 * 2. Parses `@@@task` blocks into structured [Task] objects
 * 3. Stores them in the task store via the coordinator
 * 4. Writes the task IDs to the pipeline context
 *
 * ## Early termination
 * If no `@@@task` blocks are found in the plan output, the stage returns
 * [StageResult.Done] with [OrchestratorResult.NoTasks], terminating the pipeline.
 *
 * ## Inputs (from [PipelineContext])
 * - [PipelineContext.planOutput] — the raw plan text from the planning stage
 *
 * ## Outputs (written to [PipelineContext])
 * - [PipelineContext.taskIds] — list of created task IDs
 */
class TaskRegistrationStage : PipelineStage {

    override val name = "task-registration"
    override val description = "Parses @@@task blocks from the plan and registers them"

    override suspend fun execute(context: PipelineContext): StageResult {
        val planOutput = context.planOutput
        if (planOutput.isBlank()) {
            return StageResult.Failed("No plan output available — planning stage may have failed")
        }

        val taskIds = context.system.coordinator.registerTasks(planOutput)

        if (taskIds.isEmpty()) {
            return StageResult.Done(OrchestratorResult.NoTasks(planOutput))
        }

        context.taskIds = taskIds
        context.emitPhase(OrchestratorPhase.TasksRegistered(taskIds.size))

        return StageResult.Continue
    }
}
