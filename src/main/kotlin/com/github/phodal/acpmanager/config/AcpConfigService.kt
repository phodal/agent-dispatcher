package com.github.phodal.acpmanager.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Service that manages ACP agent configurations.
 *
 * Supports two configuration sources (in priority order):
 * 1. ~/.acp-manager/config.yaml (ACP Manager specific)
 * 2. ~/.autodev/config.yaml (AutoDev/Xiuper shared config)
 *
 * The service will automatically detect agents from both sources and merge them.
 */
@Service(Service.Level.PROJECT)
class AcpConfigService(private val project: Project) {

    private val log = logger<AcpConfigService>()
    private var cachedConfig: AcpManagerConfig? = null
    private var lastModified: Long = 0

    /**
     * Get the ACP Manager config directory.
     */
    private fun getAcpManagerConfigDir(): File {
        return File(System.getProperty("user.home"), ".acp-manager").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Get the AutoDev config directory.
     */
    private fun getAutoDevConfigDir(): File {
        return File(System.getProperty("user.home"), ".autodev")
    }

    /**
     * Get the ACP Manager config file path.
     */
    fun getGlobalConfigFile(): File {
        return File(getAcpManagerConfigDir(), "config.yaml")
    }

    /**
     * Get the AutoDev config file path.
     */
    private fun getAutoDevConfigFile(): File {
        return File(getAutoDevConfigDir(), "config.yaml")
    }

    /**
     * Load the configuration, merging from AutoDev config, ACP Manager config, and detected presets.
     * 
     * Priority: ACP Manager > AutoDev > Presets (only for missing agents)
     */
    fun loadConfig(): AcpManagerConfig {
        val acpManagerFile = getGlobalConfigFile()
        val autoDevFile = getAutoDevConfigFile()

        val currentModified = maxOf(
            if (acpManagerFile.exists()) acpManagerFile.lastModified() else 0,
            if (autoDevFile.exists()) autoDevFile.lastModified() else 0
        )

        if (cachedConfig != null && currentModified == lastModified) {
            return cachedConfig!!
        }

        try {
            val yaml = Yaml()

            // 1. Load AutoDev config first (highest priority for agents)
            val autoDevAgents = if (autoDevFile.exists()) {
                loadAutoDevAcpAgents(autoDevFile, yaml)
            } else {
                emptyMap()
            }
            log.info("Loaded ${autoDevAgents.size} agents from AutoDev config")

            // 2. Load ACP Manager config
            val acpManagerAgents = if (acpManagerFile.exists()) {
                loadAcpManagerConfig(acpManagerFile, yaml)
            } else {
                emptyMap()
            }
            log.info("Loaded ${acpManagerAgents.size} agents from ACP Manager config")

            // 3. Detect presets for agents NOT already in autodev or acp-manager
            val existingAgentIds = (autoDevAgents.keys + acpManagerAgents.keys).toSet()
            val presetAgents = detectPresetsExcluding(existingAgentIds)
            log.info("Detected ${presetAgents.size} additional ACP agent presets from PATH")

            // 4. Merge configs (priority: ACP Manager > AutoDev > Presets)
            val mergedAgents = presetAgents + autoDevAgents + acpManagerAgents

            // 5. Determine active agent
            val activeAgent = when {
                acpManagerFile.exists() -> {
                    val data = yaml.load<Map<String, Any>>(acpManagerFile.reader())
                    data?.get("activeAgent") as? String
                }
                autoDevFile.exists() -> {
                    val data = yaml.load<Map<String, Any>>(autoDevFile.reader())
                    data?.get("activeAcpAgent") as? String
                }
                else -> mergedAgents.keys.firstOrNull()
            }

            cachedConfig = AcpManagerConfig(agents = mergedAgents, activeAgent = activeAgent)
            lastModified = currentModified

            log.info("Loaded ACP config: ${mergedAgents.size} agents total (${autoDevAgents.size} AutoDev + ${acpManagerAgents.size} manual + ${presetAgents.size} presets)")
        } catch (e: Exception) {
            log.warn("Failed to load ACP config: ${e.message}", e)
            cachedConfig = AcpManagerConfig()
        }

        return cachedConfig!!
    }

    /**
     * Detect installed ACP agent presets from PATH, excluding already configured agents.
     */
    private fun detectPresetsExcluding(excludeIds: Set<String>): Map<String, AcpAgentConfig> {
        return try {
            AcpAgentPresets.detectInstalled()
                .filter { preset -> preset.id !in excludeIds }
                .associate { preset -> preset.id to preset.toConfig() }
        } catch (e: Exception) {
            log.warn("Failed to detect ACP agent presets: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Load agents from ACP Manager config.yaml format.
     */
    private fun loadAcpManagerConfig(file: File, yaml: Yaml): Map<String, AcpAgentConfig> {
        val data = yaml.load<Map<String, Any>>(file.reader()) ?: return emptyMap()

        @Suppress("UNCHECKED_CAST")
        val agentsMap = data["agents"] as? Map<String, Map<String, Any>> ?: return emptyMap()

        return agentsMap.mapValues { (_, v) ->
            AcpAgentConfig(
                command = v["command"] as? String ?: "",
                args = (v["args"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                env = (v["env"] as? Map<*, *>)?.entries?.associate {
                    it.key.toString() to it.value.toString()
                } ?: emptyMap(),
                description = v["description"] as? String ?: "",
                autoApprove = v["autoApprove"] as? Boolean ?: false,
            )
        }
    }

    /**
     * Load ACP agents from AutoDev config.yaml format.
     *
     * AutoDev format:
     * ```yaml
     * acpAgents:
     *   kimi:
     *     name: "Kimi CLI"
     *     command: "kimi"
     *     args: "--acp"
     *     env: "KIMI_API_KEY=xxx"
     * activeAcpAgent: kimi
     * ```
     */
    private fun loadAutoDevAcpAgents(file: File, yaml: Yaml): Map<String, AcpAgentConfig> {
        try {
            val data = yaml.load<Map<String, Any>>(file.reader()) ?: return emptyMap()

            @Suppress("UNCHECKED_CAST")
            val acpAgentsMap = data["acpAgents"] as? Map<String, Map<String, Any>> ?: return emptyMap()

            return acpAgentsMap.mapValues { (_, v) ->
                val name = v["name"] as? String ?: ""
                val command = v["command"] as? String ?: ""
                val argsStr = v["args"] as? String ?: ""
                val envStr = v["env"] as? String ?: ""

                // Parse args from space-separated string
                val argsList = argsStr.trim()
                    .split("\\s+".toRegex())
                    .filter { it.isNotBlank() }

                // Parse env from "KEY=VALUE" lines
                val envMap = envStr.lines()
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                        val idx = trimmed.indexOf('=')
                        if (idx <= 0) return@mapNotNull null
                        val key = trimmed.substring(0, idx).trim()
                        val value = trimmed.substring(idx + 1).trim()
                        key to value
                    }
                    .toMap()

                AcpAgentConfig(
                    command = command,
                    args = argsList,
                    env = envMap,
                    description = name,
                    autoApprove = false,
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to parse AutoDev ACP agents: ${e.message}")
            return emptyMap()
        }
    }

    /**
     * Save the configuration to ACP Manager config file only.
     * (We don't modify the AutoDev config file)
     */
    fun saveConfig(config: AcpManagerConfig) {
        val globalFile = getGlobalConfigFile()
        try {
            val yaml = Yaml()
            val data = mutableMapOf<String, Any>()

            if (config.activeAgent != null) {
                data["activeAgent"] = config.activeAgent
            }

            val agentsData = config.agents.mapValues { (_, agent) ->
                mutableMapOf<String, Any>().apply {
                    put("command", agent.command)
                    if (agent.args.isNotEmpty()) put("args", agent.args)
                    if (agent.env.isNotEmpty()) put("env", agent.env)
                    if (agent.description.isNotEmpty()) put("description", agent.description)
                    if (agent.autoApprove) put("autoApprove", true)
                }
            }
            if (agentsData.isNotEmpty()) {
                data["agents"] = agentsData
            }

            globalFile.writer().use { yaml.dump(data, it) }
            cachedConfig = config
            lastModified = globalFile.lastModified()
            log.info("Saved ACP config: ${config.agents.size} agents")
        } catch (e: Exception) {
            log.warn("Failed to save ACP config: ${e.message}", e)
        }
    }

    /**
     * Get a specific agent config by key.
     */
    fun getAgentConfig(agentKey: String): AcpAgentConfig? {
        return loadConfig().agents[agentKey]
    }

    /**
     * Get all configured agent keys.
     */
    fun getAgentKeys(): List<String> {
        return loadConfig().agents.keys.toList()
    }

    /**
     * Get the active agent key.
     */
    fun getActiveAgentKey(): String? {
        return loadConfig().activeAgent
    }

    /**
     * Set the active agent.
     */
    fun setActiveAgent(agentKey: String?) {
        val config = loadConfig()
        saveConfig(config.copy(activeAgent = agentKey))
    }

    /**
     * Add or update an agent configuration.
     */
    fun addOrUpdateAgent(key: String, config: AcpAgentConfig) {
        val current = loadConfig()
        val updated = current.copy(agents = current.agents + (key to config))
        saveConfig(updated)
    }

    /**
     * Remove an agent configuration.
     */
    fun removeAgent(key: String) {
        val current = loadConfig()
        val updated = current.copy(
            agents = current.agents - key,
            activeAgent = if (current.activeAgent == key) null else current.activeAgent
        )
        saveConfig(updated)
    }

    /**
     * Reload the configuration from disk, clearing the cache.
     */
    fun reloadConfig() {
        cachedConfig = null
        lastModified = 0
        loadConfig()
    }

    private fun createDefaultConfig(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            |# ACP Manager Configuration
            |# 
            |# NOTE: ACP Manager automatically detects agents from ~/.autodev/config.yaml
            |# You can add additional agents here, or use this file exclusively.
            |#
            |# Example:
            |# activeAgent: codex
            |# agents:
            |#   codex:
            |#     command: codex
            |#     args: ["--full-auto"]
            |#     description: "OpenAI Codex CLI"
            |#   claude:
            |#     command: claude
            |#     args: []
            |#     description: "Anthropic Claude Code"
            |agents: {}
            """.trimMargin()
        )
    }

    companion object {
        fun getInstance(project: Project): AcpConfigService = project.service()
    }
}
