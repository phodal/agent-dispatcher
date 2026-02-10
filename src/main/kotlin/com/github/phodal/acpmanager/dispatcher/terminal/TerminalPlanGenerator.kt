package com.github.phodal.acpmanager.dispatcher.terminal

import com.github.phodal.acpmanager.dispatcher.PlanGenerator
import com.github.phodal.acpmanager.dispatcher.PlanParser
import com.github.phodal.acpmanager.dispatcher.model.AgentRole
import com.github.phodal.acpmanager.dispatcher.model.AgentTask
import com.github.phodal.acpmanager.dispatcher.model.DispatchPlan

/**
 * Terminal-based [PlanGenerator] implementation for E2E testing.
 *
 * Can operate in two modes:
 * 1. **Fixed plan**: Returns a preset plan (default)
 * 2. **Parse mode**: Simulates parsing a raw JSON response
 *
 * Usage:
 * ```kotlin
 * val generator = TerminalPlanGenerator()
 * generator.setFixedPlan(DispatchPlan(tasks = listOf(...)))
 * ```
 */
class TerminalPlanGenerator : PlanGenerator {

    private var fixedPlan: DispatchPlan? = null
    private var rawJsonResponse: String? = null

    /**
     * Set a fixed plan to be returned by [generatePlan].
     */
    fun setFixedPlan(plan: DispatchPlan) {
        this.fixedPlan = plan
        this.rawJsonResponse = null
    }

    /**
     * Set a raw JSON response string to be parsed.
     */
    fun setRawJsonResponse(json: String) {
        this.rawJsonResponse = json
        this.fixedPlan = null
    }

    override suspend fun generatePlan(
        masterAgentKey: String,
        userInput: String,
        availableAgents: List<AgentRole>,
    ): DispatchPlan {
        // Return raw JSON if set
        rawJsonResponse?.let {
            return PlanParser.parse(it)
        }

        // Return fixed plan if set
        fixedPlan?.let { return it }

        // Default: generate a simple plan based on user input
        return createDefaultPlan(userInput, availableAgents)
    }

    private fun createDefaultPlan(userInput: String, availableAgents: List<AgentRole>): DispatchPlan {
        val defaultAgent = availableAgents.firstOrNull()?.id

        return DispatchPlan(
            tasks = listOf(
                AgentTask(
                    id = "task-1",
                    title = "Execute: ${userInput.take(50)}",
                    description = userInput,
                    assignedAgent = defaultAgent,
                    parallelGroup = 0,
                ),
            ),
            maxParallelism = 1,
            thinking = "Simple single-task plan for: $userInput",
        )
    }
}
