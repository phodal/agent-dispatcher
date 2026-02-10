package com.phodal.routa.core.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for config loading from ~/.autodev/config.yaml.
 */
class RoutaConfigTest {

    @Test
    fun `getConfigPath returns valid path`() {
        val path = RoutaConfigLoader.getConfigPath()
        assertTrue(path.contains(".autodev"))
        assertTrue(path.contains("config.yaml"))
    }

    @Test
    fun `load returns empty config when file missing`() {
        // This test verifies graceful degradation when config doesn't exist
        // (it may or may not exist on the test machine)
        val config = RoutaConfigLoader.load()
        assertNotNull(config)
    }

    @Test
    fun `LLMProviderType fromString resolves known providers`() {
        assertEquals(LLMProviderType.OPENAI, LLMProviderType.fromString("openai"))
        assertEquals(LLMProviderType.ANTHROPIC, LLMProviderType.fromString("anthropic"))
        assertEquals(LLMProviderType.DEEPSEEK, LLMProviderType.fromString("deepseek"))
        assertEquals(LLMProviderType.GOOGLE, LLMProviderType.fromString("google"))
        assertEquals(LLMProviderType.OLLAMA, LLMProviderType.fromString("ollama"))
        assertEquals(LLMProviderType.OPENROUTER, LLMProviderType.fromString("openrouter"))
        assertNull(LLMProviderType.fromString("unknown"))
    }

    @Test
    fun `LLMProviderType fromString is case insensitive`() {
        assertEquals(LLMProviderType.OPENAI, LLMProviderType.fromString("OpenAI"))
        assertEquals(LLMProviderType.DEEPSEEK, LLMProviderType.fromString("DeepSeek"))
    }
}
