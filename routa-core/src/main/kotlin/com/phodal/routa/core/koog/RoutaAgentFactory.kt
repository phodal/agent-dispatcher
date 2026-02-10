package com.phodal.routa.core.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.*
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.phodal.routa.core.config.LLMProviderType
import com.phodal.routa.core.config.NamedModelConfig
import com.phodal.routa.core.config.RoutaConfigLoader
import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.role.RouteDefinitions
import com.phodal.routa.core.tool.AgentTools

/**
 * Factory for creating Koog [AIAgent] instances configured for Routa roles.
 *
 * Reads LLM config from `~/.autodev/config.yaml` (xiuper-compatible),
 * wires Routa coordination tools into a [ai.koog.agents.core.tools.ToolRegistry],
 * and creates agents with the appropriate system prompts.
 *
 * Usage:
 * ```kotlin
 * val factory = RoutaAgentFactory(routa.tools, "my-workspace")
 * val routaAgent = factory.createAgent(AgentRole.ROUTA)
 * val result = routaAgent.run("Implement user authentication for the API")
 * ```
 */
class RoutaAgentFactory(
    private val agentTools: AgentTools,
    private val workspaceId: String,
) {

    /**
     * Create a Koog AIAgent for the given role, using config from `~/.autodev/config.yaml`.
     *
     * @param role The agent role (ROUTA, CRAFTER, or GATE).
     * @param modelConfig Optional explicit model config (overrides config.yaml).
     * @return A configured Koog AIAgent<String, String>.
     * @throws IllegalStateException if no valid config is found.
     */
    fun createAgent(
        role: AgentRole,
        modelConfig: NamedModelConfig? = null,
    ): AIAgent<String, String> {
        val config = modelConfig ?: RoutaConfigLoader.getActiveModelConfig()
            ?: throw IllegalStateException(
                "No active model config found. Please configure ~/.autodev/config.yaml " +
                    "(path: ${RoutaConfigLoader.getConfigPath()})"
            )

        val executor = createExecutor(config)
        val model = createModel(config)
        val toolRegistry = RoutaToolRegistry.create(agentTools, workspaceId)
        val roleDefinition = RouteDefinitions.forRole(role)

        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = roleDefinition.systemPrompt,
            toolRegistry = toolRegistry,
        )
    }

    /**
     * Create a SingleLLMPromptExecutor from the config.
     */
    private fun createExecutor(config: NamedModelConfig): SingleLLMPromptExecutor {
        val provider = LLMProviderType.fromString(config.provider)
            ?: throw IllegalArgumentException("Unknown provider: ${config.provider}")

        return when (provider) {
            LLMProviderType.OPENAI -> simpleOpenAIExecutor(config.apiKey)
            LLMProviderType.ANTHROPIC -> simpleAnthropicExecutor(config.apiKey)
            LLMProviderType.GOOGLE -> simpleGoogleAIExecutor(config.apiKey)
            LLMProviderType.DEEPSEEK -> simpleOpenAIExecutor(config.apiKey)
            LLMProviderType.OLLAMA -> simpleOllamaAIExecutor(
                baseUrl = config.baseUrl.ifEmpty { "http://localhost:11434" }
            )
            LLMProviderType.OPENROUTER -> simpleOpenRouterExecutor(config.apiKey)
        }
    }

    /**
     * Create an LLModel from the config.
     */
    private fun createModel(config: NamedModelConfig): LLModel {
        val provider = LLMProviderType.fromString(config.provider)
            ?: LLMProviderType.OPENAI

        val llmProvider = when (provider) {
            LLMProviderType.OPENAI -> LLMProvider.OpenAI
            LLMProviderType.ANTHROPIC -> LLMProvider.Anthropic
            LLMProviderType.GOOGLE -> LLMProvider.Google
            LLMProviderType.DEEPSEEK -> LLMProvider.OpenAI
            LLMProviderType.OLLAMA -> LLMProvider.OpenAI
            LLMProviderType.OPENROUTER -> LLMProvider.OpenAI
        }

        return LLModel(
            provider = llmProvider,
            id = config.model,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
            ),
            contextLength = config.maxTokens.toLong(),
        )
    }
}
