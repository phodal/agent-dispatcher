package com.github.phodal.acpmanager

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.github.phodal.acpmanager.config.AcpManagerConfig
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyPluginTest : BasePlatformTestCase() {

    fun testAgentConfig() {
        val config = AcpAgentConfig(
            command = "codex",
            args = listOf("--full-auto"),
            env = mapOf("API_KEY" to "test"),
            description = "Test agent",
        )

        assertEquals("codex", config.command)
        assertEquals(listOf("--full-auto"), config.args)
        assertEquals(listOf("codex", "--full-auto"), config.getCommandLine())
    }

    fun testManagerConfig() {
        val config = AcpManagerConfig(
            agents = mapOf(
                "codex" to AcpAgentConfig(command = "codex"),
                "claude" to AcpAgentConfig(command = "claude"),
            ),
            activeAgent = "codex",
        )

        assertEquals(2, config.agents.size)
        assertEquals("codex", config.activeAgent)
    }
}
