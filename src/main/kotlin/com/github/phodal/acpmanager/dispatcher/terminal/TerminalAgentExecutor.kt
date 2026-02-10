package com.github.phodal.acpmanager.dispatcher.terminal

import com.github.phodal.acpmanager.dispatcher.AgentExecutor
import com.github.phodal.acpmanager.dispatcher.model.AgentLogEntry
import com.github.phodal.acpmanager.dispatcher.model.LogLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Terminal-based [AgentExecutor] implementation for E2E testing.
 *
 * Instead of connecting to real ACP agents, this executor simulates agent execution
 * by producing log entries with configurable delays and outcomes.
 *
 * Usage in tests:
 * ```kotlin
 * val executor = TerminalAgentExecutor()
 * executor.setSimulatedResult("task-1", listOf("Analyzing code...", "Found 3 issues", "Done."))
 * ```
 */
class TerminalAgentExecutor : AgentExecutor {

    /**
     * Simulated results per task ID. Each entry is a list of messages to emit.
     */
    private val simulatedResults = ConcurrentHashMap<String, SimulatedExecution>()

    /**
     * Available agents.
     */
    private val availableAgents = ConcurrentHashMap<String, Boolean>()

    /**
     * Track cancelled tasks.
     */
    private val cancelledTasks = ConcurrentHashMap.newKeySet<String>()

    /**
     * All emitted logs (for test assertions).
     */
    val executionLogs = mutableListOf<AgentLogEntry>()

    /**
     * Prompts received by each task (for verifying context passing).
     */
    val receivedPrompts = ConcurrentHashMap<String, String>()

    /**
     * Configure a simulated execution result.
     */
    fun setSimulatedResult(
        taskId: String,
        messages: List<String>,
        delayMs: Long = 50,
        shouldFail: Boolean = false,
        failMessage: String = "Simulated failure",
    ) {
        simulatedResults[taskId] = SimulatedExecution(messages, delayMs, shouldFail, failMessage)
    }

    /**
     * Register an agent as available.
     */
    fun registerAgent(agentKey: String) {
        availableAgents[agentKey] = true
    }

    override fun execute(agentKey: String, prompt: String, taskId: String): Flow<AgentLogEntry> = flow {
        // Record the prompt for test assertions
        receivedPrompts[taskId] = prompt

        val simulation = simulatedResults[taskId] ?: SimulatedExecution(
            messages = listOf("Executing task $taskId with agent $agentKey...", "Task completed."),
            delayMs = 50,
        )

        for (message in simulation.messages) {
            if (taskId in cancelledTasks) {
                val cancelLog = AgentLogEntry(
                    level = LogLevel.WRN,
                    source = agentKey,
                    taskId = taskId,
                    message = "Task cancelled.",
                )
                executionLogs.add(cancelLog)
                emit(cancelLog)
                return@flow
            }

            delay(simulation.delayMs)

            val logEntry = AgentLogEntry(
                level = LogLevel.INF,
                source = agentKey,
                taskId = taskId,
                message = message,
                isContent = true,
            )
            executionLogs.add(logEntry)
            emit(logEntry)
        }

        if (simulation.shouldFail) {
            throw RuntimeException(simulation.failMessage)
        }
    }

    override suspend fun cancel(taskId: String) {
        cancelledTasks.add(taskId)
    }

    override suspend fun isAvailable(agentKey: String): Boolean {
        return availableAgents[agentKey] == true
    }

    /**
     * Reset all state for a fresh test run.
     */
    fun reset() {
        simulatedResults.clear()
        availableAgents.clear()
        cancelledTasks.clear()
        executionLogs.clear()
        receivedPrompts.clear()
    }
}

/**
 * Configuration for a simulated task execution.
 */
data class SimulatedExecution(
    val messages: List<String>,
    val delayMs: Long = 50,
    val shouldFail: Boolean = false,
    val failMessage: String = "Simulated failure",
)
