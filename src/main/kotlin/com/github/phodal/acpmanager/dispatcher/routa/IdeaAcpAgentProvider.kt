package com.github.phodal.acpmanager.dispatcher.routa

import com.github.phodal.acpmanager.acp.AcpSessionManager
import com.github.phodal.acpmanager.config.AcpConfigService
import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.provider.AgentProvider
import com.phodal.routa.core.provider.ProviderCapabilities
import com.phodal.routa.core.provider.StreamChunk
import com.phodal.routa.core.provider.ThinkingPhase
import com.phodal.routa.core.provider.ToolCallStatus
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

private val log = logger<IdeaAcpAgentProvider>()

/**
 * AgentProvider implementation that bridges routa-core with IDEA's AcpSessionManager.
 *
 * Maps routa agent IDs to ACP sessions, routing each role (ROUTA, CRAFTER, GATE)
 * to the appropriate configured ACP agent. Supports streaming output from agents.
 *
 * Each routa agent gets its own unique ACP session (keyed as "routa-{agentId}")
 * so multiple CRAFTERs can run in parallel with independent sessions.
 */
class IdeaAcpAgentProvider(
    private val project: Project,
    private val scope: CoroutineScope,
    /** ACP agent key to use for CRAFTER agents (e.g., "codex", "claude-code"). */
    private val crafterAgentKey: String,
    /** ACP agent key to use for GATE agents. Defaults to crafterAgentKey. */
    private val gateAgentKey: String = crafterAgentKey,
    /** ACP agent key to use for ROUTA agent. Defaults to crafterAgentKey. */
    private val routaAgentKey: String = crafterAgentKey,
) : AgentProvider {

    /** Maps routa agentId → ACP session key for cleanup. */
    private val sessionMapping = ConcurrentHashMap<String, String>()

    override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
        return runStreaming(role, agentId, prompt) { /* discard chunks */ }
    }

    override suspend fun runStreaming(
        role: AgentRole,
        agentId: String,
        prompt: String,
        onChunk: (StreamChunk) -> Unit,
    ): String {
        val acpKey = getAgentKeyForRole(role)
        val sessionKey = "routa-${agentId.take(8)}"
        sessionMapping[agentId] = sessionKey

        log.info("Running agent $agentId (role=$role) via ACP agent '$acpKey', session='$sessionKey'")

        val sessionManager = AcpSessionManager.getInstance(project)
        val session = sessionManager.getOrCreateSession(sessionKey)

        // Connect if needed
        if (!session.isConnected) {
            val config = AcpConfigService.getInstance(project).getAgentConfig(acpKey)
                ?: throw IllegalStateException("ACP agent '$acpKey' not configured")
            session.connect(config)
        }

        // Collect streaming events in parallel
        val outputBuilder = StringBuilder()
        val completionDeferred = CompletableDeferred<Unit>()

        val collectJob = scope.launch {
            session.renderEvents.collect { event ->
                when (event) {
                    is RenderEvent.MessageChunk -> {
                        onChunk(StreamChunk.Text(event.content))
                    }

                    is RenderEvent.MessageEnd -> {
                        outputBuilder.append(event.fullContent)
                    }

                    is RenderEvent.ToolCallStart -> {
                        onChunk(
                            StreamChunk.ToolCall(
                                name = event.toolName,
                                status = ToolCallStatus.STARTED,
                                arguments = event.title,
                            )
                        )
                    }

                    is RenderEvent.ToolCallEnd -> {
                        onChunk(
                            StreamChunk.ToolCall(
                                name = event.title ?: "tool",
                                status = if (event.status == com.agentclientprotocol.model.ToolCallStatus.COMPLETED)
                                    ToolCallStatus.COMPLETED else ToolCallStatus.FAILED,
                                result = event.output,
                            )
                        )
                    }

                    is RenderEvent.ThinkingStart -> {
                        onChunk(StreamChunk.Thinking("", ThinkingPhase.START))
                    }

                    is RenderEvent.ThinkingChunk -> {
                        onChunk(StreamChunk.Thinking(event.content, ThinkingPhase.CHUNK))
                    }

                    is RenderEvent.ThinkingEnd -> {
                        onChunk(StreamChunk.Thinking(event.fullContent, ThinkingPhase.END))
                    }

                    is RenderEvent.Error -> {
                        onChunk(StreamChunk.Error(event.message))
                    }

                    is RenderEvent.PromptComplete -> {
                        onChunk(StreamChunk.Completed(event.stopReason ?: "end"))
                        completionDeferred.complete(Unit)
                    }

                    else -> { /* ignore other events */ }
                }
            }
        }

        try {
            // Send the prompt (suspends until complete)
            session.sendMessage(prompt)

            // Wait for the PromptComplete event to propagate
            try {
                withTimeout(5000) { completionDeferred.await() }
            } catch (_: TimeoutCancellationException) {
                log.warn("Timed out waiting for PromptComplete event for agent $agentId")
            }
        } finally {
            collectJob.cancel()
        }

        return outputBuilder.toString()
    }

    override fun isHealthy(agentId: String): Boolean {
        val sessionKey = sessionMapping[agentId] ?: return false
        val session = AcpSessionManager.getInstance(project).getSession(sessionKey)
        return session?.isConnected == true
    }

    override suspend fun interrupt(agentId: String) {
        val sessionKey = sessionMapping[agentId] ?: return
        val session = AcpSessionManager.getInstance(project).getSession(sessionKey)
        session?.cancelPrompt()
    }

    override fun capabilities(): ProviderCapabilities {
        return ProviderCapabilities(
            name = "IDEA ACP Provider",
            supportsStreaming = true,
            supportsInterrupt = true,
            supportsHealthCheck = true,
            supportsFileEditing = true,
            supportsTerminal = true,
            supportsToolCalling = true, // ACP agents handle tools internally (e.g., Claude Code)
            maxConcurrentAgents = 5,
            priority = 10,
        )
    }

    override suspend fun cleanup(agentId: String) {
        val sessionKey = sessionMapping.remove(agentId) ?: return
        log.info("Cleaning up session '$sessionKey' for agent $agentId")
        val sessionManager = AcpSessionManager.getInstance(project)
        sessionManager.disconnectAgent(sessionKey)
    }

    override suspend fun shutdown() {
        sessionMapping.keys.toList().forEach { agentId ->
            cleanup(agentId)
        }
    }

    private fun getAgentKeyForRole(role: AgentRole): String = when (role) {
        AgentRole.ROUTA -> routaAgentKey
        AgentRole.CRAFTER -> crafterAgentKey
        AgentRole.GATE -> gateAgentKey
    }
}
