package com.github.phodal.acpmanager.dispatcher.idea

import com.github.phodal.acpmanager.acp.AcpSessionManager
import com.github.phodal.acpmanager.dispatcher.PlanGenerator
import com.github.phodal.acpmanager.dispatcher.PlanParser
import com.github.phodal.acpmanager.dispatcher.model.AgentRole
import com.github.phodal.acpmanager.dispatcher.model.DispatchPlan
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*

private val log = logger<IdeaPlanGenerator>()

/**
 * IDEA-specific [PlanGenerator] that uses a real ACP agent (the Master Agent)
 * to generate a structured plan from user input.
 *
 * The Master Agent is prompted with a system prompt that requests JSON output,
 * and the response is parsed by [PlanParser].
 *
 * IMPORTANT: `session.sendMessage()` is a blocking suspend call that internally
 * collects the ACP prompt flow until completion. Events are emitted to
 * `session.renderEvents` (a SharedFlow with replay=0) DURING the sendMessage call.
 * Therefore we must start collecting renderEvents BEFORE calling sendMessage.
 */
class IdeaPlanGenerator(
    private val project: Project,
) : PlanGenerator {

    private val sessionManager = AcpSessionManager.getInstance(project)

    override suspend fun generatePlan(
        masterAgentKey: String,
        userInput: String,
        availableAgents: List<AgentRole>,
    ): DispatchPlan {
        log.info("Generating plan using master agent '$masterAgentKey'")

        // Build the planning prompt
        val prompt = PlanParser.buildPlanningPrompt(userInput, availableAgents)

        // Ensure master agent is connected
        val existingSession = sessionManager.getSession(masterAgentKey)
        if (existingSession == null || !existingSession.isConnected) {
            log.info("Master agent '$masterAgentKey' not connected, connecting...")
            sessionManager.connectAgent(masterAgentKey)
        }

        val session = sessionManager.getSession(masterAgentKey)
            ?: throw RuntimeException("Failed to get session for master agent '$masterAgentKey'")

        val responseBuilder = StringBuilder()
        val chunkBuilder = StringBuilder()
        val completion = CompletableDeferred<Unit>()

        // Start collecting renderEvents BEFORE calling sendMessage,
        // because sendMessage blocks and emits events during execution.
        val collectJob = CoroutineScope(currentCoroutineContext()).launch {
            session.renderEvents.collect { event ->
                when (event) {
                    is RenderEvent.MessageEnd -> {
                        // MessageEnd contains the full content — use it and discard chunks
                        responseBuilder.clear()
                        responseBuilder.append(event.fullContent)
                    }
                    is RenderEvent.MessageChunk -> {
                        // Accumulate chunks as fallback
                        chunkBuilder.append(event.content)
                    }
                    is RenderEvent.Error -> {
                        log.warn("Error during plan generation: ${event.message}")
                        completion.completeExceptionally(
                            RuntimeException("Plan generation error: ${event.message}")
                        )
                    }
                    is RenderEvent.PromptComplete -> {
                        log.info("Plan generation prompt completed")
                        completion.complete(Unit)
                    }
                    else -> {} // Ignore other events
                }
            }
        }

        try {
            // Now send the message — this blocks until agent finishes
            log.info("Sending planning prompt to '$masterAgentKey' (${prompt.length} chars)...")
            session.sendMessage(prompt)
            log.info("sendMessage returned for '$masterAgentKey'")

            // sendMessage blocks until done, so PromptComplete should have fired.
            // Wait with a short timeout in case events are still being delivered.
            withTimeoutOrNull(5000) {
                completion.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("sendMessage failed for '$masterAgentKey': ${e.message}", e)
            throw RuntimeException("Master agent communication failed: ${e.message}", e)
        } finally {
            collectJob.cancel()
        }

        var rawResponse = responseBuilder.toString()
        // Fallback to accumulated chunks if MessageEnd wasn't emitted
        if (rawResponse.isBlank() && chunkBuilder.isNotBlank()) {
            rawResponse = chunkBuilder.toString()
        }
        if (rawResponse.isBlank()) {
            throw RuntimeException("Master agent '$masterAgentKey' returned empty response")
        }

        log.info("Parsing plan from response (${rawResponse.length} chars)")

        return try {
            PlanParser.parse(rawResponse)
        } catch (e: Exception) {
            log.warn("Failed to parse plan response: $rawResponse", e)
            throw RuntimeException("Failed to parse Master Agent response: ${e.message}", e)
        }
    }
}
