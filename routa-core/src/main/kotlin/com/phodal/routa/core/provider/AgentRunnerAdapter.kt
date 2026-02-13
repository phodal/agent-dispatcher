package com.phodal.routa.core.provider

import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.runner.AgentRunner

/**
 * Adapter that wraps a legacy [AgentRunner] into an [AgentProvider].
 *
 * This enables backward compatibility with code that still uses
 * the simpler [AgentRunner] interface (e.g., tests with mock runners).
 *
 * The adapter provides:
 * - [run]: Delegates directly to the wrapped runner.
 * - [runStreaming]: Falls back to [run] and emits the full result as a single [StreamChunk.Text].
 * - [capabilities]: Returns minimal capabilities (no streaming, no file editing, etc.).
 *
 * ## Usage
 * ```kotlin
 * val runner: AgentRunner = MockRunner()
 * val provider: AgentProvider = AgentRunnerAdapter(runner)
 * val orchestrator = RoutaOrchestrator(system, provider, "workspace")
 * ```
 */
class AgentRunnerAdapter(
    private val runner: AgentRunner,
) : AgentProvider {

    override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
        return runner.run(role, agentId, prompt)
    }

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        name = "AgentRunnerAdapter(${runner::class.simpleName})",
        supportsStreaming = false,
        supportsToolCalling = false,
        supportsFileEditing = false,
        supportsTerminal = false,
    )
}
