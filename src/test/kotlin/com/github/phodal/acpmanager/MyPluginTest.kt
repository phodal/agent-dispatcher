package com.github.phodal.acpmanager

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.github.phodal.acpmanager.config.AcpAgentPresets
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

    fun testClaudeCodeConfig() {
        // Claude Code uses no args (native ACP support)
        val config = AcpAgentConfig(
            command = "/opt/homebrew/bin/claude",
            args = emptyList(),
            description = "Claude Code",
        )

        assertEquals(listOf("/opt/homebrew/bin/claude"), config.getCommandLine())
        assertTrue(config.args.isEmpty())
    }

    fun testClaudeCodeEmptyArgsFromString() {
        // Simulate what happens when args: "" is parsed from YAML
        val argsStr = ""
        val argsList = argsStr.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        assertTrue(argsList.isEmpty())

        val config = AcpAgentConfig(
            command = "/opt/homebrew/bin/claude",
            args = argsList,
            description = "Claude Code",
        )

        assertEquals(listOf("/opt/homebrew/bin/claude"), config.getCommandLine())
    }

    fun testAgentPresetsIncludeClaudeCode() {
        val claudePreset = AcpAgentPresets.allPresets.find { it.id == "claude" }
        assertNotNull("Claude Code preset should exist", claudePreset)
        assertEquals("Claude Code", claudePreset!!.name)
        assertTrue("Claude Code uses empty args (native ACP)", claudePreset.args.isEmpty())
        assertTrue("Claude Code uses non-standard API", claudePreset.nonStandardApi)
    }

    fun testAgentPresetsContainAllKnownAgents() {
        val presetIds = AcpAgentPresets.allPresets.map { it.id }.toSet()
        assertTrue("opencode" in presetIds)
        assertTrue("kimi" in presetIds)
        assertTrue("gemini" in presetIds)
        assertTrue("claude" in presetIds)
        assertTrue("codex" in presetIds)
        assertTrue("copilot" in presetIds)
        assertTrue("auggie" in presetIds)
    }

    fun testAgentPresetsHaveCorrectArgs() {
        val presets = AcpAgentPresets.allPresets.associateBy { it.id }

        assertEquals(listOf("acp"), presets["opencode"]!!.args)
        assertEquals(listOf("acp"), presets["kimi"]!!.args)
        assertEquals(listOf("--experimental-acp"), presets["gemini"]!!.args)
        assertEquals(emptyList<String>(), presets["claude"]!!.args)
//        assertEquals(listOf("--acp"), presets["codex"]!!.args)
//        assertEquals(listOf("--acp"), presets["copilot"]!!.args)
//        assertEquals(listOf("--acp"), presets["auggie"]!!.args)
    }

    fun testPresetToConfig() {
        val claudePreset = AcpAgentPresets.allPresets.find { it.id == "claude" }!!
        val config = claudePreset.toConfig()

        assertEquals("claude", config.command)
        assertEquals(emptyList<String>(), config.args)
        assertEquals("Claude Code", config.description)
        assertFalse(config.autoApprove)
        assertEquals(listOf("claude"), config.getCommandLine())
    }

    fun testAutoDevConfigFormat() {
        // Simulate loading the user's autodev config format
        val agentConfigs = mapOf(
            "opencode" to AcpAgentConfig(
                command = "/Users/phodal/.opencode/bin/opencode",
                args = listOf("acp"),
                description = "OpenCode",
            ),
            "kimi" to AcpAgentConfig(
                command = "/Library/Frameworks/Python.framework/Versions/3.12/bin/kimi",
                args = listOf("acp"),
                description = "Kimi",
            ),
            "gemini" to AcpAgentConfig(
                command = "/opt/homebrew/bin/gemini",
                args = listOf("--experimental-acp"),
                description = "Gemini",
            ),
            "copilot" to AcpAgentConfig(
                command = "/opt/homebrew/bin/copilot",
                args = listOf("--acp"),
                description = "Copilot",
            ),
            "claude" to AcpAgentConfig(
                command = "/opt/homebrew/bin/claude",
                args = emptyList(),
                description = "Claude Code",
            ),
            "auggie" to AcpAgentConfig(
                command = "auggie",
                args = listOf("--acp"),
                description = "Auggie",
            ),
        )

        val config = AcpManagerConfig(
            agents = agentConfigs,
            activeAgent = "opencode",
        )

        assertEquals(6, config.agents.size)
        assertEquals("opencode", config.activeAgent)

        // Verify command lines
        assertEquals(
            listOf("/Users/phodal/.opencode/bin/opencode", "acp"),
            config.agents["opencode"]!!.getCommandLine()
        )
        assertEquals(
            listOf("/opt/homebrew/bin/claude"),
            config.agents["claude"]!!.getCommandLine()
        )
        assertEquals(
            listOf("/opt/homebrew/bin/gemini", "--experimental-acp"),
            config.agents["gemini"]!!.getCommandLine()
        )
        assertEquals(
            listOf("/opt/homebrew/bin/copilot", "--acp"),
            config.agents["copilot"]!!.getCommandLine()
        )
    }

    fun testManagerConfigMerging() {
        // Test that configs merge correctly (ACP Manager > AutoDev > Presets)
        val presetAgents = mapOf(
            "codex" to AcpAgentConfig(command = "codex", args = listOf("--acp"), description = "Codex (preset)"),
            "claude" to AcpAgentConfig(command = "claude", description = "Claude (preset)"),
        )

        val autoDevAgents = mapOf(
            "claude" to AcpAgentConfig(
                command = "/opt/homebrew/bin/claude",
                description = "Claude Code (autodev)",
            ),
            "kimi" to AcpAgentConfig(
                command = "/Library/Frameworks/Python.framework/Versions/3.12/bin/kimi",
                args = listOf("acp"),
                description = "Kimi (autodev)",
            ),
        )

        val acpManagerAgents = mapOf(
            "kimi" to AcpAgentConfig(
                command = "/custom/path/kimi",
                args = listOf("acp", "--verbose"),
                description = "Kimi (custom)",
            ),
        )

        // Merge: presets + autodev + acpManager (later overrides earlier)
        val merged = presetAgents + autoDevAgents + acpManagerAgents

        assertEquals(3, merged.size)

        // codex from presets (not overridden)
        assertEquals("codex", merged["codex"]!!.command)
        assertEquals("Codex (preset)", merged["codex"]!!.description)

        // claude from autodev (overrides preset)
        assertEquals("/opt/homebrew/bin/claude", merged["claude"]!!.command)
        assertEquals("Claude Code (autodev)", merged["claude"]!!.description)

        // kimi from acpManager (overrides autodev)
        assertEquals("/custom/path/kimi", merged["kimi"]!!.command)
        assertEquals(listOf("acp", "--verbose"), merged["kimi"]!!.args)
        assertEquals("Kimi (custom)", merged["kimi"]!!.description)
    }
}
