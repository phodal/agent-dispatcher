package com.github.phodal.acpmanager.dispatcher.idea

import com.github.phodal.acpmanager.acp.AcpSessionManager
import com.github.phodal.acpmanager.dispatcher.AgentExecutor
import com.github.phodal.acpmanager.dispatcher.model.AgentLogEntry
import com.github.phodal.acpmanager.dispatcher.model.LogLevel
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

private val log = logger<IdeaAgentExecutor>()

/**
 * IDEA-specific [AgentExecutor] that delegates to [AcpSessionManager] to
 * execute prompts against real ACP agents.
 *
 * IMPORTANT: `session.sendMessage()` is blocking — it internally collects the
 * full ACP prompt flow and emits events to `session.renderEvents` during execution.
 * We must subscribe to renderEvents BEFORE calling sendMessage.
 */
class IdeaAgentExecutor(
    private val project: Project,
) : AgentExecutor {

    private val sessionManager = AcpSessionManager.getInstance(project)
    private val cancelledTasks = ConcurrentHashMap.newKeySet<String>()

    override fun execute(agentKey: String, prompt: String, taskId: String): Flow<AgentLogEntry> = flow {
        log.info("Executing task $taskId with agent $agentKey")

        emit(
            AgentLogEntry(
                level = LogLevel.INF,
                source = agentKey,
                taskId = taskId,
                message = "Starting execution with agent '$agentKey'...",
            )
        )

        try {
            // Ensure agent is connected
            val session = sessionManager.getSession(agentKey)
            if (session == null || !session.isConnected) {
                emit(
                    AgentLogEntry(
                        level = LogLevel.INF,
                        source = agentKey,
                        taskId = taskId,
                        message = "Connecting to agent '$agentKey'...",
                    )
                )
                sessionManager.connectAgent(agentKey)
            }

            val activeSession = sessionManager.getSession(agentKey)
                ?: throw IllegalStateException("Failed to get session for agent '$agentKey'")

            // Use a channel to bridge renderEvents (collected in a separate coroutine)
            // into the flow we're emitting from.
            val logChannel = Channel<AgentLogEntry>(capacity = Channel.BUFFERED)
            val completion = CompletableDeferred<Unit>()

            // Start collecting renderEvents BEFORE calling sendMessage
            val collectJob = CoroutineScope(currentCoroutineContext()).launch {
                activeSession.renderEvents.collect { event ->
                    if (taskId in cancelledTasks) {
                        logChannel.send(
                            AgentLogEntry(
                                level = LogLevel.WRN,
                                source = agentKey,
                                taskId = taskId,
                                message = "Task cancelled.",
                            )
                        )
                        completion.complete(Unit)
                        return@collect
                    }

                    val logMsg = renderEventToLogMessage(event)
                    if (logMsg != null) {
                        logChannel.send(
                            AgentLogEntry(
                                level = if (event is RenderEvent.Error) LogLevel.ERR else LogLevel.INF,
                                source = agentKey,
                                taskId = taskId,
                                message = logMsg,
                            )
                        )
                    }

                    if (event is RenderEvent.PromptComplete) {
                        completion.complete(Unit)
                    }
                }
            }

            // Send prompt in a separate coroutine (it blocks until done)
            val sendJob = CoroutineScope(currentCoroutineContext()).launch {
                try {
                    activeSession.sendMessage(prompt)
                    // sendMessage returned — give events a moment to propagate
                    withTimeoutOrNull(3000) { completion.await() }
                } catch (e: Exception) {
                    logChannel.send(
                        AgentLogEntry(
                            level = LogLevel.ERR,
                            source = agentKey,
                            taskId = taskId,
                            message = "Agent error: ${e.message}",
                        )
                    )
                } finally {
                    logChannel.close()
                }
            }

            // Emit log entries from the channel
            for (logEntry in logChannel) {
                emit(logEntry)
            }

            // Clean up
            sendJob.join()
            collectJob.cancel()

            emit(
                AgentLogEntry(
                    level = LogLevel.INF,
                    source = agentKey,
                    taskId = taskId,
                    message = "Execution completed.",
                )
            )
        } catch (e: Exception) {
            log.warn("Task $taskId execution failed", e)
            emit(
                AgentLogEntry(
                    level = LogLevel.ERR,
                    source = agentKey,
                    taskId = taskId,
                    message = "Execution failed: ${e.message}",
                )
            )
            throw e
        }
    }

    override suspend fun cancel(taskId: String) {
        cancelledTasks.add(taskId)
    }

    override suspend fun isAvailable(agentKey: String): Boolean {
        return try {
            val session = sessionManager.getSession(agentKey)
            session?.isConnected == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Convert a render event to a simple log message string.
     * Returns null for events that shouldn't be logged.
     */
    private fun renderEventToLogMessage(event: RenderEvent): String? {
        return when (event) {
            is RenderEvent.MessageEnd -> event.fullContent.take(200)
            is RenderEvent.ToolCallStart -> "Tool: ${event.toolName} — ${event.title ?: ""}"
            is RenderEvent.ToolCallEnd -> "Tool completed: ${event.title ?: event.toolCallId}"
            is RenderEvent.Error -> "Error: ${event.message}"
            is RenderEvent.Info -> event.message
            is RenderEvent.PromptComplete -> "Prompt complete (${event.stopReason ?: "done"})"
            else -> null
        }
    }
}
