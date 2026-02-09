package com.github.phodal.acpmanager.config

import kotlinx.serialization.Serializable

/**
 * Configuration for a single ACP agent.
 *
 * Example YAML:
 * ```yaml
 * agents:
 *   codex:
 *     command: codex
 *     args: ["--full-auto"]
 *     env:
 *       OPENAI_API_KEY: "sk-..."
 *   claude:
 *     command: claude
 *     args: []
 * ```
 */
@Serializable
data class AcpAgentConfig(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val description: String = "",
    val autoApprove: Boolean = false,
    /**
     * Whether this agent uses a non-standard ACP API.
     * Claude Code uses stream-json mode instead of standard ACP protocol.
     */
    val nonStandardApi: Boolean = false,
    /**
     * List of tools to auto-approve for Claude Code.
     * Uses Claude Code's permission rule syntax, e.g., "Bash", "Read", "Edit", "Bash(git *)".
     * When set, these tools will be auto-approved without prompting.
     * Only applicable when using Claude Code (stream-json mode).
     */
    val allowedTools: List<String> = emptyList(),
) {
    fun getCommandLine(): List<String> {
        return mutableListOf(command).apply { addAll(args) }
    }

    /**
     * Check if this is a Claude Code agent based on command name or nonStandardApi flag.
     */
    fun isClaudeCode(): Boolean {
        return nonStandardApi || command.endsWith("claude") || command.contains("/claude")
    }
}

/**
 * Root configuration for the ACP Manager.
 */
@Serializable
data class AcpManagerConfig(
    val agents: Map<String, AcpAgentConfig> = emptyMap(),
    val activeAgent: String? = null,
)
