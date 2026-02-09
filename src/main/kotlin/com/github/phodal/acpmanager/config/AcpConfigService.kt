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
 * Reads from ~/.acp-manager/config.yaml and supports per-project overrides.
 */
@Service(Service.Level.PROJECT)
class AcpConfigService(private val project: Project) {

    private val log = logger<AcpConfigService>()
    private var cachedConfig: AcpManagerConfig? = null
    private var lastModified: Long = 0

    /**
     * Get the global config directory.
     */
    private fun getGlobalConfigDir(): File {
        return File(System.getProperty("user.home"), ".acp-manager").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Get the global config file path.
     */
    fun getGlobalConfigFile(): File {
        return File(getGlobalConfigDir(), "config.yaml")
    }

    /**
     * Load the configuration, merging global and project-level configs.
     */
    fun loadConfig(): AcpManagerConfig {
        val globalFile = getGlobalConfigFile()
        if (!globalFile.exists()) {
            // Create default config
            createDefaultConfig(globalFile)
        }

        val currentModified = globalFile.lastModified()
        if (cachedConfig != null && currentModified == lastModified) {
            return cachedConfig!!
        }

        try {
            val yaml = Yaml()
            val data = globalFile.reader().use { yaml.load<Map<String, Any>>(it) } ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val agentsMap = data["agents"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val activeAgent = data["activeAgent"] as? String

            val agents = agentsMap.mapValues { (_, v) ->
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

            cachedConfig = AcpManagerConfig(agents = agents, activeAgent = activeAgent)
            lastModified = currentModified
            log.info("Loaded ACP config: ${agents.size} agents configured")
        } catch (e: Exception) {
            log.warn("Failed to load ACP config: ${e.message}", e)
            cachedConfig = AcpManagerConfig()
        }

        return cachedConfig!!
    }

    /**
     * Save the configuration to disk.
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
            |# Add your ACP-compatible agents here.
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
