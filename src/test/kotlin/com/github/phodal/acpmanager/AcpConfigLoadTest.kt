package com.github.phodal.acpmanager

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.github.phodal.acpmanager.config.AcpManagerConfig
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.yaml.snakeyaml.Yaml

/**
 * Tests for ACP config loading, especially the AutoDev config.yaml format.
 * Ensures compatibility with the user's actual configuration.
 */
class AcpConfigLoadTest : BasePlatformTestCase() {

    /**
     * Parse agents from AutoDev config.yaml format (same logic as AcpConfigService).
     */
    private fun loadAutoDevAcpAgents(yamlContent: String): Map<String, AcpAgentConfig> {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(yamlContent) ?: return emptyMap()

        @Suppress("UNCHECKED_CAST")
        val acpAgentsMap = data["acpAgents"] as? Map<String, Map<String, Any>> ?: return emptyMap()

        return acpAgentsMap.mapValues { (_, v) ->
            val name = v["name"] as? String ?: ""
            val command = v["command"] as? String ?: ""
            val argsStr = v["args"] as? String ?: ""
            val envStr = v["env"] as? String ?: ""

            val argsList = argsStr.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }

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
    }

    fun testLoadUserAutoDevConfig() {
        val yamlContent = """
            acpAgents:
              "opencode":
                name: "OpenCode"
                command: "/Users/phodal/.opencode/bin/opencode"
                args: "acp"
                env: ""
              "kimi":
                name: "Kimi"
                command: "/Library/Frameworks/Python.framework/Versions/3.12/bin/kimi"
                args: "acp"
                env: ""
              "gemini":
                name: "Gemini"
                command: "/opt/homebrew/bin/gemini"
                args: "--experimental-acp"
                env: ""
              "copilot":
                name: "Copilot"
                command: "/opt/homebrew/bin/copilot"
                args: "--acp"
                env: ""
              "claude":
                name: "Claude Code"
                command: "/opt/homebrew/bin/claude"
                args: ""
                env: ""
              "auggie":
                name: "Auggie"
                command: "auggie"
                args: "--acp"
                env: ""
            activeAcpAgent: "opencode"
        """.trimIndent()

        val agents = loadAutoDevAcpAgents(yamlContent)

        assertEquals(6, agents.size)

        // OpenCode
        val opencode = agents["opencode"]!!
        assertEquals("/Users/phodal/.opencode/bin/opencode", opencode.command)
        assertEquals(listOf("acp"), opencode.args)
        assertEquals("OpenCode", opencode.description)
        assertEquals(
            listOf("/Users/phodal/.opencode/bin/opencode", "acp"),
            opencode.getCommandLine()
        )

        // Kimi
        val kimi = agents["kimi"]!!
        assertEquals("/Library/Frameworks/Python.framework/Versions/3.12/bin/kimi", kimi.command)
        assertEquals(listOf("acp"), kimi.args)
        assertEquals("Kimi", kimi.description)

        // Gemini
        val gemini = agents["gemini"]!!
        assertEquals("/opt/homebrew/bin/gemini", gemini.command)
        assertEquals(listOf("--experimental-acp"), gemini.args)
        assertEquals("Gemini", gemini.description)

        // Copilot
        val copilot = agents["copilot"]!!
        assertEquals("/opt/homebrew/bin/copilot", copilot.command)
        assertEquals(listOf("--acp"), copilot.args)
        assertEquals("Copilot", copilot.description)

        // Claude Code - key: empty args means native ACP
        val claude = agents["claude"]!!
        assertEquals("/opt/homebrew/bin/claude", claude.command)
        assertTrue("Claude Code should have empty args for native ACP", claude.args.isEmpty())
        assertEquals("Claude Code", claude.description)
        assertEquals(
            listOf("/opt/homebrew/bin/claude"),
            claude.getCommandLine()
        )

        // Auggie
        val auggie = agents["auggie"]!!
        assertEquals("auggie", auggie.command)
        assertEquals(listOf("--acp"), auggie.args)
        assertEquals("Auggie", auggie.description)
    }

    fun testLoadActiveAcpAgent() {
        val yamlContent = """
            acpAgents:
              "opencode":
                name: "OpenCode"
                command: "opencode"
                args: "acp"
                env: ""
            activeAcpAgent: "opencode"
        """.trimIndent()

        val yaml = Yaml()
        val rawData: Any = yaml.load(yamlContent) ?: fail("Failed to parse YAML")
        @Suppress("UNCHECKED_CAST")
        val data = rawData as Map<String, Any>
        val activeAgent = data["activeAcpAgent"] as? String

        assertEquals("opencode", activeAgent)
    }

    fun testEmptyArgsHandling() {
        // Test various forms of empty args
        val testCases = mapOf<String, List<String>>(
            "" to emptyList(),
            " " to emptyList(),
            "  " to emptyList(),
        )

        for ((input, expected) in testCases) {
            val result = input.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
            assertEquals("Input '$input' should produce empty list", expected, result)
        }
    }

    fun testSingleArgHandling() {
        val testCases = mapOf(
            "acp" to listOf("acp"),
            "--acp" to listOf("--acp"),
            "--experimental-acp" to listOf("--experimental-acp"),
            "--full-auto" to listOf("--full-auto"),
        )

        for ((input, expected) in testCases) {
            val result = input.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
            assertEquals("Input '$input'", expected, result)
        }
    }

    fun testMultiArgHandling() {
        val input = "acp --verbose --debug"
        val result = input.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
        assertEquals(listOf("acp", "--verbose", "--debug"), result)
    }

    fun testEnvParsing() {
        val envStr = "API_KEY=sk-123\nMODEL=gpt-4"
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

        assertEquals(2, envMap.size)
        assertEquals("sk-123", envMap["API_KEY"])
        assertEquals("gpt-4", envMap["MODEL"])
    }

    fun testEnvEmptyString() {
        val envStr = ""
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

        assertTrue(envMap.isEmpty())
    }

    fun testClaudeCodeNativeAcpCommandLine() {
        // Claude Code runs without any --acp flag
        // It natively supports ACP protocol
        val config = AcpAgentConfig(
            command = "/opt/homebrew/bin/claude",
            args = emptyList(),
            description = "Claude Code",
        )

        val cmdLine = config.getCommandLine()
        assertEquals(1, cmdLine.size)
        assertEquals("/opt/homebrew/bin/claude", cmdLine[0])
    }

    fun testAllAgentsHaveValidCommandLines() {
        val agents = mapOf(
            "opencode" to AcpAgentConfig(
                command = "/Users/phodal/.opencode/bin/opencode",
                args = listOf("acp"),
            ),
            "kimi" to AcpAgentConfig(
                command = "/Library/Frameworks/Python.framework/Versions/3.12/bin/kimi",
                args = listOf("acp"),
            ),
            "gemini" to AcpAgentConfig(
                command = "/opt/homebrew/bin/gemini",
                args = listOf("--experimental-acp"),
            ),
            "copilot" to AcpAgentConfig(
                command = "/opt/homebrew/bin/copilot",
                args = listOf("--acp"),
            ),
            "claude" to AcpAgentConfig(
                command = "/opt/homebrew/bin/claude",
                args = emptyList(),
            ),
            "auggie" to AcpAgentConfig(
                command = "auggie",
                args = listOf("--acp"),
            ),
        )

        for ((key, config) in agents) {
            val cmdLine = config.getCommandLine()
            assertTrue("Agent '$key' should have non-empty command line", cmdLine.isNotEmpty())
            assertTrue("Agent '$key' command should not be blank", cmdLine[0].isNotBlank())
        }
    }
}
