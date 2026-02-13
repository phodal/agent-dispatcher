package com.phodal.routa.core.pipeline

import com.phodal.routa.core.pipeline.stages.CrafterExecutionStage
import com.phodal.routa.core.pipeline.stages.GateVerificationStage
import com.phodal.routa.core.pipeline.stages.PlanningStage
import com.phodal.routa.core.pipeline.stages.TaskRegistrationStage
import com.phodal.routa.core.runner.OrchestratorPhase
import com.phodal.routa.core.runner.OrchestratorResult

/**
 * A composable orchestration pipeline that executes [PipelineStage]s in sequence.
 *
 * The pipeline is the heart of the multi-agent workflow. It replaces the monolithic
 * `RoutaOrchestrator.execute()` method with a declarative, extensible stage list
 * that reads like a workflow specification.
 *
 * ## Execution Model
 *
 * ```
 * for iteration in 1..maxIterations:
 *     for stage in stages:
 *         result = stage.execute(context)
 *         match result:
 *             Continue       → next stage
 *             SkipRemaining  → return result (success)
 *             RepeatPipeline → break to next iteration
 *             Done           → return result
 *             Failed         → return failure
 *     if all stages returned Continue:
 *         return success
 * if maxIterations exhausted:
 *     return MaxWavesReached
 * ```
 *
 * ## Composability
 *
 * ```kotlin
 * // Standard ROUTA → CRAFTER → GATE pipeline
 * val standard = OrchestrationPipeline.default()
 *
 * // Simple pipeline (skip verification)
 * val simple = OrchestrationPipeline(listOf(
 *     PlanningStage(),
 *     TaskRegistrationStage(),
 *     CrafterExecutionStage(),
 * ))
 *
 * // Extended pipeline (add custom stages)
 * val extended = OrchestrationPipeline(listOf(
 *     PlanningStage(),
 *     TaskRegistrationStage(),
 *     CrafterExecutionStage(),
 *     CodeReviewStage(),          // custom stage
 *     GateVerificationStage(),
 * ))
 * ```
 *
 * ## Testability
 *
 * Each stage can be tested in isolation:
 * ```kotlin
 * val stage = PlanningStage()
 * val context = PipelineContext(mockSystem, mockProvider, "ws", "request", parser)
 * val result = stage.execute(context)
 * assertEquals(StageResult.Continue, result)
 * assertNotBlank(context.planOutput)
 * ```
 *
 * @param stages The ordered list of stages to execute.
 * @param maxIterations Maximum number of pipeline iterations (prevents infinite fix loops).
 *
 * @see PipelineStage for implementing custom stages.
 * @see PipelineContext for the shared state between stages.
 */
class OrchestrationPipeline(
    val stages: List<PipelineStage>,
    val maxIterations: Int = 3,
) {

    /**
     * Execute the pipeline with the given context.
     *
     * @param context The pipeline context containing system, provider, and configuration.
     * @return The final [OrchestratorResult].
     */
    suspend fun execute(context: PipelineContext): OrchestratorResult {
        context.emitPhase(OrchestratorPhase.Initializing)

        for (iteration in 1..maxIterations) {
            var shouldRepeat = false

            for (stage in stages) {
                val result = stage.execute(context)

                when (result) {
                    is StageResult.Continue -> {
                        // Proceed to next stage
                        continue
                    }

                    is StageResult.SkipRemaining -> {
                        return result.result
                    }

                    is StageResult.RepeatPipeline -> {
                        // Break out of stage loop, continue outer iteration loop
                        shouldRepeat = true
                        break
                    }

                    is StageResult.Done -> {
                        return result.result
                    }

                    is StageResult.Failed -> {
                        return OrchestratorResult.Failed(result.error)
                    }
                }
            }

            if (!shouldRepeat) {
                // All stages returned Continue — pipeline completed normally
                context.emitPhase(OrchestratorPhase.Completed)
                val summary = context.system.coordinator.getTaskSummary()
                return OrchestratorResult.Success(summary)
            }
        }

        // Max iterations exhausted
        context.emitPhase(OrchestratorPhase.MaxWavesReached(maxIterations))
        val summary = context.system.coordinator.getTaskSummary()
        return OrchestratorResult.MaxWavesReached(maxIterations, summary)
    }

    /**
     * Create a new pipeline with an additional stage appended.
     */
    fun withStage(stage: PipelineStage): OrchestrationPipeline {
        return OrchestrationPipeline(stages + stage, maxIterations)
    }

    /**
     * Create a new pipeline with a stage inserted at the given index.
     */
    fun withStageAt(index: Int, stage: PipelineStage): OrchestrationPipeline {
        val newStages = stages.toMutableList().apply { add(index, stage) }
        return OrchestrationPipeline(newStages, maxIterations)
    }

    /**
     * Create a new pipeline with a different max iterations setting.
     */
    fun withMaxIterations(max: Int): OrchestrationPipeline {
        return OrchestrationPipeline(stages, max)
    }

    /**
     * Get a human-readable description of this pipeline.
     */
    fun describe(): String = buildString {
        appendLine("OrchestrationPipeline (maxIterations=$maxIterations)")
        stages.forEachIndexed { index, stage ->
            appendLine("  ${index + 1}. [${stage.name}] ${stage.description}")
        }
    }

    companion object {
        /**
         * Create the default ROUTA → CRAFTER → GATE pipeline.
         *
         * This is the standard multi-agent orchestration workflow:
         * 1. **Planning**: ROUTA analyzes the request and creates @@@task blocks
         * 2. **Task Registration**: Parse tasks and store them
         * 3. **CRAFTER Execution**: Execute tasks with implementation agents
         * 4. **GATE Verification**: Verify work against acceptance criteria
         *
         * If GATE rejects, the pipeline repeats from CRAFTER Execution (up to maxIterations).
         */
        fun default(maxIterations: Int = 3): OrchestrationPipeline {
            return OrchestrationPipeline(
                stages = listOf(
                    PlanningStage(),
                    TaskRegistrationStage(),
                    CrafterExecutionStage(),
                    GateVerificationStage(),
                ),
                maxIterations = maxIterations,
            )
        }

        /**
         * Create a simple pipeline without verification.
         *
         * Useful for quick iterations where GATE verification is not needed.
         */
        fun withoutVerification(maxIterations: Int = 1): OrchestrationPipeline {
            return OrchestrationPipeline(
                stages = listOf(
                    PlanningStage(),
                    TaskRegistrationStage(),
                    CrafterExecutionStage(),
                ),
                maxIterations = maxIterations,
            )
        }
    }
}
