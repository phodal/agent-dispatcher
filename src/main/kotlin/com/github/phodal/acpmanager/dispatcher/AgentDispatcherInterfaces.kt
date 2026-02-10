package com.github.phodal.acpmanager.dispatcher

import com.github.phodal.acpmanager.dispatcher.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract interface for executing prompts against an ACP agent.
 *
 * Implementations:
 * - [TerminalAgentExecutor]: For testing — prints to terminal/stdout
 * - IdeaAgentExecutor: Uses the real AcpSessionManager in IntelliJ IDEA
 */
interface AgentExecutor {

    /**
     * Send a prompt to the specified ACP agent and stream log entries back.
     *
     * @param agentKey The agent identifier (must match a configured ACP agent)
     * @param prompt   The prompt text to send
     * @param taskId   The task this execution belongs to
     * @return Flow of log entries produced during execution
     */
    fun execute(agentKey: String, prompt: String, taskId: String): Flow<AgentLogEntry>

    /**
     * Cancel any in-progress execution for the given task.
     */
    suspend fun cancel(taskId: String)

    /**
     * Check if the agent is available/connected.
     */
    suspend fun isAvailable(agentKey: String): Boolean
}

/**
 * Abstract interface for plan generation using a Master Agent.
 *
 * The Master Agent receives user input and produces a structured plan
 * (list of tasks with optional parallelism and agent assignments).
 *
 * Implementations:
 * - [TerminalPlanGenerator]: For testing — returns a hardcoded plan
 * - IdeaPlanGenerator: Uses a real ACP agent to generate plans
 */
interface PlanGenerator {

    /**
     * Generate a dispatch plan from user input.
     *
     * The Master Agent is prompted with a system prompt that requests
     * JSON/XML formatted output, which is then parsed into a [DispatchPlan].
     *
     * @param masterAgentKey The ACP agent to use as the planner
     * @param userInput      The user's task description
     * @param availableAgents The set of available agent keys
     * @return The generated plan
     */
    suspend fun generatePlan(
        masterAgentKey: String,
        userInput: String,
        availableAgents: List<AgentRole>,
    ): DispatchPlan
}

/**
 * The main multi-agent dispatcher that orchestrates plan generation and task execution.
 *
 * Lifecycle:
 * 1. User provides input → Master Agent generates a plan
 * 2. User reviews/edits the plan (assign agents, toggle parallelism)
 * 3. User clicks "Execute" → Dispatcher runs tasks respecting dependencies and parallelism
 * 4. Logs are streamed in real-time per task
 */
interface AgentDispatcher {

    /**
     * Observable state of the dispatcher.
     */
    val state: StateFlow<DispatcherState>

    /**
     * Observable log stream.
     */
    val logStream: Flow<AgentLogEntry>

    /**
     * Start planning: send user input to the Master Agent.
     */
    suspend fun startPlanning(userInput: String)

    /**
     * Update a task's assigned agent.
     */
    fun updateTaskAgent(taskId: String, agentKey: String)

    /**
     * Update the maximum parallelism for execution.
     */
    fun updateMaxParallelism(maxParallelism: Int)

    /**
     * Execute the current plan.
     */
    suspend fun executePlan()

    /**
     * Cancel all running tasks.
     */
    suspend fun cancelAll()

    /**
     * Set the master agent used for planning.
     */
    fun setMasterAgent(agentKey: String)

    /**
     * Set the available agent roles.
     */
    fun setAgentRoles(roles: List<AgentRole>)

    /**
     * Reset the dispatcher to idle state.
     */
    fun reset()
}
