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
) {
    fun getCommandLine(): List<String> {
        return mutableListOf(command).apply { addAll(args) }
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
