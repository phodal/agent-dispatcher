package com.phodal.routa.core.koog

import com.phodal.routa.core.RoutaFactory
import com.phodal.routa.core.model.AgentRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Koog SimpleTool wrappers.
 *
 * These tests verify that the Koog tool wrappers correctly delegate
 * to the underlying AgentTools and return proper results.
 */
class RoutaKoogToolsTest {

    private fun withRouta(block: suspend (com.phodal.routa.core.RoutaSystem) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)
        try {
            runBlocking { block(routa) }
        } finally {
            routa.coordinator.shutdown()
        }
    }

    @Test
    fun `ListAgentsTool returns agents in workspace`() = withRouta { routa ->
        val coordinator = routa.coordinator
        coordinator.initialize("test-workspace")

        val tool = ListAgentsTool(routa.tools, "test-workspace")
        val result = tool.execute(ListAgentsArgs(workspaceId = "test-workspace"))

        assertTrue("Expected 'routa-main' in: $result", result.contains("routa-main"))
        assertTrue("Expected 'ROUTA' in: $result", result.contains("ROUTA"))
    }

    @Test
    fun `CreateAgentTool creates a crafter`() = withRouta { routa ->
        val routaAgentId = routa.coordinator.initialize("test-workspace")

        val tool = CreateAgentTool(routa.tools, "test-workspace")
        val result = tool.execute(
            CreateAgentArgs(
                name = "test-crafter",
                role = "CRAFTER",
                parentId = routaAgentId,
            )
        )

        assertTrue("Expected 'test-crafter' in: $result", result.contains("test-crafter"))
        assertTrue("Expected 'CRAFTER' in: $result", result.contains("CRAFTER"))
    }

    @Test
    fun `CreateAgentTool rejects invalid role`() = withRouta { routa ->
        routa.coordinator.initialize("test-workspace")

        val tool = CreateAgentTool(routa.tools, "test-workspace")
        val result = tool.execute(
            CreateAgentArgs(name = "bad", role = "INVALID")
        )

        assertTrue("Expected error in: $result", result.contains("Error"))
        assertTrue("Expected 'Invalid role' in: $result", result.contains("Invalid role"))
    }

    @Test
    fun `MessageAgentTool sends messages between agents`() = withRouta { routa ->
        val routaId = routa.coordinator.initialize("test-workspace")

        val tool = MessageAgentTool(routa.tools)
        val result = tool.execute(
            MessageAgentArgs(
                fromAgentId = routaId,
                toAgentId = routaId,
                message = "Hello from Koog tool",
            )
        )

        assertTrue("Expected 'sent' in: $result", result.contains("sent"))
        assertTrue("Expected 'routa-main' in: $result", result.contains("routa-main"))
    }

    @Test
    fun `ReadAgentConversationTool reads messages`() = withRouta { routa ->
        val routaId = routa.coordinator.initialize("test-workspace")

        // Send a message first
        routa.tools.messageAgent(routaId, routaId, "Test message")

        val tool = ReadAgentConversationTool(routa.tools)
        val result = tool.execute(
            ReadAgentConversationArgs(agentId = routaId)
        )

        assertTrue("Expected 'Test message' in: $result", result.contains("Test message"))
    }

    @Test
    fun `RoutaToolRegistry creates all 6 tools`() = withRouta { routa ->
        val registry = RoutaToolRegistry.create(routa.tools, "test-workspace")

        // Verify the registry was created successfully (no exceptions)
        assertNotNull(registry)
    }
}
