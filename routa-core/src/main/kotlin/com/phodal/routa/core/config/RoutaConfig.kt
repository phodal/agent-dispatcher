package com.phodal.routa.core.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import java.io.File

/**
 * LLM provider types supported by Routa, matching xiuper's config format.
 */
@Serializable
enum class LLMProviderType(val displayName: String) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GOOGLE("google"),
    DEEPSEEK("deepseek"),
    OLLAMA("ollama"),
    OPENROUTER("openrouter");

    companion object {
        fun fromString(name: String): LLMProviderType? {
            return entries.find { it.displayName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Named model configuration, matching xiuper's config.yaml format.
 */
@Serializable
data class NamedModelConfig(
    val name: String = "",
    val provider: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.0,
    val maxTokens: Int = 128000,
    val baseUrl: String = "",
)

/**
 * Root configuration file, compatible with `~/.autodev/config.yaml`.
 *
 * ```yaml
 * active: default
 * configs:
 *   - name: default
 *     provider: deepseek
 *     apiKey: sk-...
 *     model: deepseek-chat
 * ```
 */
@Serializable
data class RoutaConfigFile(
    val active: String = "",
    val configs: List<NamedModelConfig> = emptyList(),
)

/**
 * Loads Routa configuration from `~/.autodev/config.yaml`.
 *
 * This is compatible with xiuper's configuration format, so the same
 * config file can be shared between projects.
 */
object RoutaConfigLoader {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
        )
    )

    private val configDir: File by lazy {
        File(System.getProperty("user.home"), ".autodev")
    }

    private val configFile: File by lazy {
        File(configDir, "config.yaml")
    }

    /**
     * Load config from `~/.autodev/config.yaml`.
     *
     * @return The parsed config, or a default empty config if the file doesn't exist.
     */
    fun load(): RoutaConfigFile {
        if (!configFile.exists()) {
            return RoutaConfigFile()
        }

        return try {
            val content = configFile.readText()
            yaml.decodeFromString(RoutaConfigFile.serializer(), content)
        } catch (e: Exception) {
            System.err.println("Warning: Failed to parse ~/.autodev/config.yaml: ${e.message}")
            RoutaConfigFile()
        }
    }

    /**
     * Get the active model configuration.
     */
    fun getActiveModelConfig(): NamedModelConfig? {
        val config = load()
        if (config.active.isEmpty() || config.configs.isEmpty()) {
            return config.configs.firstOrNull()
        }
        return config.configs.find { it.name == config.active }
            ?: config.configs.firstOrNull()
    }

    /**
     * Check if a valid config exists.
     */
    fun hasConfig(): Boolean {
        val active = getActiveModelConfig() ?: return false
        return when {
            active.provider.equals("ollama", ignoreCase = true) -> active.model.isNotEmpty()
            else -> active.apiKey.isNotEmpty() && active.model.isNotEmpty()
        }
    }

    /**
     * Get the config file path for display/debugging.
     */
    fun getConfigPath(): String = configFile.absolutePath
}
