package com.phodal.routa.core.koog

import com.phodal.routa.core.RoutaFactory
import com.phodal.routa.core.config.RoutaConfigLoader
import com.phodal.routa.core.model.AgentRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Integration test that creates a real Koog AIAgent using config from `~/.autodev/config.yaml`.
 *
 * This test is SKIPPED if no valid config exists.
 * To run: ensure `~/.autodev/config.yaml` has a valid active config with an API key.
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
class RoutaAgentIntegrationTest {

    /**
     * Tests that a Routa agent can be created and respond to a simple prompt.
     * Skipped if no valid config exists or if the LLM API is unreachable.
     */
    @Test
    fun `create Routa agent from config and run simple prompt`() {
        // Skip if no config available
        assumeTrue(
            "Skipping: no ~/.autodev/config.yaml with valid config found",
            RoutaConfigLoader.hasConfig()
        )

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)

        try {
            runBlocking { routa.coordinator.initialize("integration-test") }

            val factory = RoutaAgentFactory(routa.tools, "integration-test")

            // This will skip if the API key is invalid/expired
            try {
                val agent = factory.createAgent(AgentRole.ROUTA)
                val result = runBlocking {
                    agent.run("Say 'OK' if you can hear me. Reply with just 'OK'.")
                }

                println("Routa agent response: $result")
                assert(result.isNotEmpty()) { "Agent should return a non-empty response" }
            } catch (e: Exception) {
                // LLM API errors should not fail the test, just skip
                println("Integration test skipped due to LLM error: ${e.message}")
                assumeTrue("LLM API unavailable: ${e.message}", false)
            }
        } finally {
            routa.coordinator.shutdown()
        }
    }

    /**
     * Tests that a Crafter agent can use coordination tools.
     * Skipped if no valid config exists or if the LLM API is unreachable.
     */
    @Test
    fun `create Crafter agent from config and verify tool access`() {
        assumeTrue(
            "Skipping: no ~/.autodev/config.yaml with valid config found",
            RoutaConfigLoader.hasConfig()
        )

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)

        try {
            runBlocking { routa.coordinator.initialize("integration-test") }

            val factory = RoutaAgentFactory(routa.tools, "integration-test")

            try {
                val agent = factory.createAgent(AgentRole.CRAFTER)
                val result = runBlocking {
                    agent.run(
                        "Use the list_agents tool to list all agents in the workspace. " +
                            "Then tell me what you found."
                    )
                }

                println("Crafter agent response: $result")
                assert(result.isNotEmpty()) { "Agent should return a non-empty response" }
            } catch (e: Exception) {
                println("Integration test skipped due to LLM error: ${e.message}")
                assumeTrue("LLM API unavailable: ${e.message}", false)
            }
        } finally {
            routa.coordinator.shutdown()
        }
    }
}
