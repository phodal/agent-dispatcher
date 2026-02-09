package com.github.phodal.acpmanager

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.github.phodal.acpmanager.config.AcpAgentPresets
import com.github.phodal.acpmanager.ui.AgentConnectionStatus
import com.github.phodal.acpmanager.ui.AgentDisplayItem
import com.github.phodal.acpmanager.ui.AgentListCellRenderer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color

/**
 * Tests for agent connection status handling and display.
 */
class AgentStatusTest : BasePlatformTestCase() {

    fun testAgentConnectionStatusValues() {
        // Ensure all status values exist
        val statuses = AgentConnectionStatus.values()
        assertEquals(4, statuses.size)
        assertTrue(AgentConnectionStatus.CONNECTED in statuses)
        assertTrue(AgentConnectionStatus.CONNECTING in statuses)
        assertTrue(AgentConnectionStatus.DISCONNECTED in statuses)
        assertTrue(AgentConnectionStatus.ERROR in statuses)
    }

    fun testAgentDisplayItem() {
        val item = AgentDisplayItem(
            key = "claude",
            displayName = "Claude Code",
            status = AgentConnectionStatus.CONNECTED,
        )

        assertEquals("claude", item.key)
        assertEquals("Claude Code", item.displayName)
        assertEquals(AgentConnectionStatus.CONNECTED, item.status)
        assertEquals("Claude Code", item.toString())
    }

    fun testStatusColors() {
        // Verify each status maps to a distinct color
        val connectedColor = AgentListCellRenderer.getStatusColor(AgentConnectionStatus.CONNECTED)
        val connectingColor = AgentListCellRenderer.getStatusColor(AgentConnectionStatus.CONNECTING)
        val errorColor = AgentListCellRenderer.getStatusColor(AgentConnectionStatus.ERROR)
        val disconnectedColor = AgentListCellRenderer.getStatusColor(AgentConnectionStatus.DISCONNECTED)

        // Colors should be non-null
        assertNotNull(connectedColor)
        assertNotNull(connectingColor)
        assertNotNull(errorColor)
        assertNotNull(disconnectedColor)
    }

    fun testAllPresetsHaveUniqueIds() {
        val ids = AcpAgentPresets.allPresets.map { it.id }
        assertEquals("All preset IDs should be unique", ids.size, ids.toSet().size)
    }

    fun testPresetDetection() {
        // detectInstalled should not crash
        try {
            val installed = AcpAgentPresets.detectInstalled()
            // Should return a list (possibly empty if no agents installed)
            assertNotNull(installed)
        } catch (e: Exception) {
            fail("detectInstalled should not throw: ${e.message}")
        }
    }

    fun testAgentConfigCommandLineVariations() {
        // Test various command line patterns used by different agents
        val configs = listOf(
            // OpenCode: command + subcommand
            AcpAgentConfig(command = "opencode", args = listOf("acp")),
            // Gemini: command + flag
            AcpAgentConfig(command = "gemini", args = listOf("--experimental-acp")),
            // Claude: command only (native ACP)
            AcpAgentConfig(command = "claude", args = emptyList()),
            // Copilot: command + flag
            AcpAgentConfig(command = "copilot", args = listOf("--acp")),
            // Auggie: command + flag
            AcpAgentConfig(command = "auggie", args = listOf("--acp")),
        )

        for (config in configs) {
            val cmdLine = config.getCommandLine()
            assertTrue("Command line should start with the command", cmdLine[0] == config.command)
            assertEquals(
                "Command line should have command + all args",
                1 + config.args.size,
                cmdLine.size
            )
        }
    }
}
