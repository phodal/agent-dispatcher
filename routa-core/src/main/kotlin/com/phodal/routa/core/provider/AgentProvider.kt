package com.phodal.routa.core.provider

import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.runner.AgentRunner
import java.time.Instant

/**
 * Extended provider abstraction for running agents.
 *
 * Goes beyond the basic [AgentRunner] interface by adding:
 * - **Health checks** — detect stale/crashed agents
 * - **Interruption** — cancel a running agent (e.g., on timeout)
 * - **Streaming** — receive incremental output via [StreamChunk] callbacks
 * - **Capability declarations** — enable dynamic routing without hardcoded role→runner mapping
 * - **Resource cleanup** — proper lifecycle management per agent
 *
 * Inspired by Intent's `BaseAgentProvider` pattern: each provider declares
 * what it can do, and the [CapabilityBasedRouter] picks the best provider
 * for each role at runtime.
 *
 * Implementations:
 * - [AcpAgentProvider] — spawns real coding agents via ACP protocol
 * - [KoogAgentProvider] — uses JetBrains Koog AIAgent for LLM calls
 * - [ClaudeAgentProvider] — wraps Claude Code CLI
 * - [ResilientAgentProvider] — decorator adding circuit breaker + session recovery
 *
 * @see AgentRunner for the minimal backward-compatible interface
 * @see CapabilityBasedRouter for dynamic provider selection
 */
interface AgentProvider : AgentRunner {

    /**
     * Check if the agent identified by [agentId] is alive and responsive.
     *
     * Used by the coordinator's staleness detector to find crashed agents.
     * Providers that manage child processes should check if the process is alive.
     * Providers backed by remote APIs should check last-seen heartbeat.
     *
     * @return `true` if the agent is healthy, `false` if stale or dead.
     */
    fun isHealthy(agentId: String): Boolean = true

    /**
     * Interrupt a running agent.
     *
     * Called when:
     * - Agent exceeds its time budget
     * - Task is cancelled by the coordinator
     * - A conflict is detected that requires aborting work
     *
     * After interruption, the agent's status should transition to CANCELLED or ERROR.
     * Implementations should be idempotent (safe to call multiple times).
     */
    suspend fun interrupt(agentId: String) {}

    /**
     * Run an agent with streaming output.
     *
     * This is the preferred execution method for providers that support it.
     * Each [StreamChunk] is delivered as it arrives, enabling:
     * - Real-time progress display in the UI
     * - Heartbeat tracking for staleness detection
     * - Early conflict detection based on partial output
     *
     * The default implementation falls back to [run] with no streaming.
     *
     * @param role The agent's role (for selecting model/config).
     * @param agentId The agent's ID in the store.
     * @param prompt The input prompt for the agent.
     * @param onChunk Callback invoked for each streaming chunk.
     * @return The agent's complete text output.
     */
    suspend fun runStreaming(
        role: AgentRole,
        agentId: String,
        prompt: String,
        onChunk: (StreamChunk) -> Unit,
    ): String {
        // Default: non-streaming fallback
        val result = run(role, agentId, prompt)
        onChunk(StreamChunk.Text(result))
        return result
    }

    /**
     * Declare what this provider can do.
     *
     * Used by [CapabilityBasedRouter] to select the best provider for a role.
     * Providers should return stable capabilities (don't change between calls).
     */
    fun capabilities(): ProviderCapabilities

    /**
     * Clean up resources for a specific agent.
     *
     * Called after the agent completes (successfully or with error).
     * Implementations should release processes, connections, temp files, etc.
     * Must be idempotent.
     */
    suspend fun cleanup(agentId: String) {}

    /**
     * Clean up all resources managed by this provider.
     *
     * Called during system shutdown.
     */
    suspend fun shutdown() {}
}

// ── Capabilities ────────────────────────────────────────────────────────

/**
 * Declares what a provider can do.
 *
 * Modeled after Intent's `resolveProviderCapabilities()` pattern:
 * each provider advertises its features, and the router uses them
 * for dynamic selection instead of hardcoded role→runner mapping.
 */
data class ProviderCapabilities(
    /** Human-readable provider name (e.g., "Koog LLM", "ACP Codex", "Claude CLI"). */
    val name: String,

    /** Whether the provider supports streaming output via [AgentProvider.runStreaming]. */
    val supportsStreaming: Boolean = false,

    /** Whether the provider supports [AgentProvider.interrupt]. */
    val supportsInterrupt: Boolean = false,

    /** Whether [AgentProvider.isHealthy] returns meaningful results. */
    val supportsHealthCheck: Boolean = false,

    /** Whether the provider can edit files (Crafter requirement). */
    val supportsFileEditing: Boolean = false,

    /** Whether the provider can run shell commands (verification). */
    val supportsTerminal: Boolean = false,

    /** Whether the provider supports LLM tool calling (function calls). */
    val supportsToolCalling: Boolean = false,

    /** Maximum number of agents this provider can run concurrently. */
    val maxConcurrentAgents: Int = 1,

    /** Selection priority — higher wins when multiple providers match. */
    val priority: Int = 0,
) {
    /**
     * Check if this provider satisfies the given requirements.
     */
    fun satisfies(requirements: ProviderRequirements): Boolean {
        if (requirements.needsFileEditing && !supportsFileEditing) return false
        if (requirements.needsTerminal && !supportsTerminal) return false
        if (requirements.needsToolCalling && !supportsToolCalling) return false
        if (requirements.needsStreaming && !supportsStreaming) return false
        return true
    }
}

/**
 * Requirements that a provider must satisfy for a given role.
 *
 * @see AgentRole.requirements for the default requirements per role.
 */
data class ProviderRequirements(
    val needsFileEditing: Boolean = false,
    val needsTerminal: Boolean = false,
    val needsToolCalling: Boolean = false,
    val needsStreaming: Boolean = false,
)

/**
 * Extension: get default provider requirements for a role.
 */
fun AgentRole.requirements(): ProviderRequirements = when (this) {
    AgentRole.ROUTA -> ProviderRequirements(
        needsToolCalling = true, // Needs to call create_agent, delegate, etc.
    )
    AgentRole.CRAFTER -> ProviderRequirements(
        needsFileEditing = true,
        needsTerminal = true,
    )
    AgentRole.GATE -> ProviderRequirements(
        needsTerminal = true, // Needs to run verification commands
    )
}

// ── Stream Chunks ───────────────────────────────────────────────────────

/**
 * Incremental output from a streaming agent execution.
 *
 * Inspired by Intent's `ACPProviderStreaming` chunk types:
 * - Text content, thinking, tool calls, heartbeats, errors, completion signals.
 */
sealed class StreamChunk {
    /** A piece of text output from the agent. */
    data class Text(val content: String) : StreamChunk()

    /**
     * Agent thinking/reasoning content (extended thinking).
     *
     * Thinking content should be rendered differently from regular text:
     * - Collapsible panel that expands during streaming
     * - Collapses automatically when thinking ends
     * - Subtle styling to distinguish from main output
     */
    data class Thinking(
        val content: String,
        val phase: ThinkingPhase = ThinkingPhase.CHUNK,
    ) : StreamChunk()

    /** Agent is invoking a tool. */
    data class ToolCall(
        val name: String,
        val status: ToolCallStatus,
        val arguments: String? = null,
        val result: String? = null,
    ) : StreamChunk()

    /** Agent is alive — used for staleness detection. */
    data class Heartbeat(val timestamp: Instant = Instant.now()) : StreamChunk()

    /** Non-fatal error during execution. */
    data class Error(val message: String, val recoverable: Boolean = true) : StreamChunk()

    /** Agent execution completed. */
    data class Completed(
        val stopReason: String,
        val totalTokens: Int? = null,
    ) : StreamChunk()

    /**
     * Completion report from an agent.
     * Used to display the final summary when an agent finishes its task.
     */
    data class CompletionReport(
        val agentId: String,
        val taskId: String,
        val summary: String,
        val filesModified: List<String> = emptyList(),
        val success: Boolean = true,
    ) : StreamChunk()
}

/**
 * Phase of thinking content for proper UI rendering.
 */
enum class ThinkingPhase {
    /** Thinking has started - create the thinking panel. */
    START,
    /** A chunk of thinking content - append to the panel. */
    CHUNK,
    /** Thinking has ended - finalize and collapse the panel. */
    END,
}

enum class ToolCallStatus {
    STARTED, IN_PROGRESS, COMPLETED, FAILED
}
