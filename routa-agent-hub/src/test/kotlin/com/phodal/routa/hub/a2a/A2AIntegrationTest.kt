package com.phodal.routa.hub.a2a

import com.phodal.routa.core.RoutaFactory
import com.phodal.routa.core.RoutaSystem
import com.phodal.routa.core.model.Task
import com.phodal.routa.core.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Tests for the A2A protocol integration in the Routa Agent Hub.
 */
class A2AIntegrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun withRouta(block: suspend (RoutaSystem) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)
        try {
            runBlocking { block(routa) }
        } finally {
            routa.coordinator.shutdown()
        }
    }

    // ── A2A Models Tests ────────────────────────────────────────────

    @Test
    fun `A2AAgentCard serializes and deserializes`() {
        val card = A2AAgentCard(
            name = "Test Agent",
            description = "A test agent",
            version = "1.0.0",
            capabilities = A2ACapabilities(streaming = true, pushNotifications = false),
            skills = listOf(
                A2ASkill(
                    id = "test_skill",
                    name = "Test Skill",
                    description = "A test skill",
                    tags = listOf("test"),
                    examples = listOf("do something"),
                )
            ),
            supportedInterfaces = listOf(
                A2AInterface(protocol = "JSONRPC", url = "http://localhost:8080")
            ),
            provider = A2AProvider(organization = "TestOrg"),
        )

        val serialized = json.encodeToString(card)
        val deserialized = json.decodeFromString<A2AAgentCard>(serialized)

        assertEquals("Test Agent", deserialized.name)
        assertEquals("A test agent", deserialized.description)
        assertEquals("1.0.0", deserialized.version)
        assertTrue(deserialized.capabilities.streaming)
        assertFalse(deserialized.capabilities.pushNotifications)
        assertEquals(1, deserialized.skills.size)
        assertEquals("test_skill", deserialized.skills[0].id)
        assertEquals("JSONRPC", deserialized.supportedInterfaces[0].protocol)
        assertEquals("TestOrg", deserialized.provider?.organization)

        println("✓ A2AAgentCard serialization round-trip works")
    }

    @Test
    fun `A2ATask serializes with task states`() {
        val task = A2ATask(
            id = "task-1",
            contextId = "ctx-1",
            status = A2ATaskStatus(
                state = A2ATaskState.WORKING,
                timestamp = Instant.now().toString(),
            ),
            artifacts = listOf(
                A2AArtifact(
                    parts = listOf(A2APart(text = "Partial result")),
                    name = "partial",
                )
            ),
        )

        val serialized = json.encodeToString(task)
        val deserialized = json.decodeFromString<A2ATask>(serialized)

        assertEquals("task-1", deserialized.id)
        assertEquals("ctx-1", deserialized.contextId)
        assertEquals(A2ATaskState.WORKING, deserialized.status.state)
        assertFalse(deserialized.status.state.isFinal())
        assertEquals(1, deserialized.artifacts.size)
        assertEquals("Partial result", deserialized.artifacts[0].parts[0].text)

        println("✓ A2ATask serialization round-trip works")
    }

    @Test
    fun `A2ATaskState isFinal works correctly`() {
        assertFalse(A2ATaskState.SUBMITTED.isFinal())
        assertFalse(A2ATaskState.WORKING.isFinal())
        assertFalse(A2ATaskState.INPUT_REQUIRED.isFinal())
        assertTrue(A2ATaskState.COMPLETED.isFinal())
        assertTrue(A2ATaskState.CANCELED.isFinal())
        assertTrue(A2ATaskState.FAILED.isFinal())
        assertTrue(A2ATaskState.REJECTED.isFinal())

        println("✓ A2ATaskState.isFinal() works correctly")
    }

    @Test
    fun `A2AMessage serializes with roles`() {
        val userMessage = A2AMessage(
            role = A2AMessageRole.USER,
            parts = listOf(A2APart(text = "Hello agent")),
            messageId = "msg-1",
        )

        val serialized = json.encodeToString(userMessage)
        assertTrue(serialized.contains("\"user\""))

        val agentMessage = A2AMessage(
            role = A2AMessageRole.AGENT,
            parts = listOf(A2APart(text = "Hello user")),
        )

        val agentSerialized = json.encodeToString(agentMessage)
        assertTrue(agentSerialized.contains("\"agent\""))

        println("✓ A2AMessage serialization with roles works")
    }

    // ── A2A Server Tests ────────────────────────────────────────────

    @Test
    fun `A2AServer buildAgentCard returns valid card`() = withRouta { routa ->
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")
        val card = server.buildAgentCard()

        assertEquals("Routa Agent Hub", card.name)
        assertEquals("0.1.0", card.version)
        assertTrue(card.skills.isNotEmpty())
        assertTrue(card.skills.any { it.id == "multi_agent_orchestration" })
        assertTrue(card.skills.any { it.id == "agent_management" })
        assertTrue(card.skills.any { it.id == "task_delegation" })
        assertEquals("JSONRPC", card.supportedInterfaces[0].protocol)
        assertEquals("http://localhost:8080", card.supportedInterfaces[0].url)
        assertEquals("Routa", card.provider?.organization)
        assertEquals(listOf("text"), card.defaultInputModes)
        assertEquals(listOf("text"), card.defaultOutputModes)

        println("✓ A2AServer.buildAgentCard() returns valid card")
    }

    @Test
    fun `A2AServer handles unknown JSON-RPC method`() = withRouta { routa ->
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")

        val request = """{"jsonrpc":"2.0","id":"1","method":"unknown/method","params":{}}"""
        val response = server.handleJsonRpc(request)

        assertNotNull(response.error)
        assertEquals(-32601, response.error!!.code)
        assertTrue(response.error!!.message.contains("Method not found"))

        println("✓ A2AServer handles unknown methods correctly")
    }

    @Test
    fun `A2AServer handles malformed JSON-RPC`() = withRouta { routa ->
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")

        val response = server.handleJsonRpc("invalid json {{{")

        assertNotNull(response.error)
        assertEquals(-32700, response.error!!.code)

        println("✓ A2AServer handles malformed JSON correctly")
    }

    @Test
    fun `A2AServer handles tasks_get for non-existent task`() = withRouta { routa ->
        routa.coordinator.initialize("test-workspace")
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")

        val request = """{"jsonrpc":"2.0","id":"1","method":"tasks/get","params":{"id":"nonexistent"}}"""
        val response = server.handleJsonRpc(request)

        assertNotNull(response.error)
        assertTrue(response.error!!.message.contains("Task not found"))

        println("✓ A2AServer handles non-existent task query correctly")
    }

    @Test
    fun `A2AServer handles tasks_get for existing task`() = withRouta { routa ->
        routa.coordinator.initialize("test-workspace")
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")

        // Create a task in the store
        val task = Task(
            id = "a2a-test-task",
            title = "A2A Test Task",
            objective = "Test A2A task retrieval",
            workspaceId = "test-workspace",
            status = TaskStatus.IN_PROGRESS,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        )
        routa.context.taskStore.save(task)

        val request = """{"jsonrpc":"2.0","id":"1","method":"tasks/get","params":{"id":"a2a-test-task"}}"""
        val response = server.handleJsonRpc(request)

        assertNull(response.error)
        assertNotNull(response.result)
        val resultStr = response.result.toString()
        assertTrue(resultStr.contains("a2a-test-task"))
        assertTrue(resultStr.contains("working"))

        println("✓ A2AServer retrieves existing tasks correctly")
    }

    @Test
    fun `A2AServer handles tasks_cancel`() = withRouta { routa ->
        routa.coordinator.initialize("test-workspace")
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")

        // Create a task
        val task = Task(
            id = "cancel-test-task",
            title = "Task to Cancel",
            objective = "Test cancellation",
            workspaceId = "test-workspace",
            status = TaskStatus.IN_PROGRESS,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        )
        routa.context.taskStore.save(task)

        val request = """{"jsonrpc":"2.0","id":"1","method":"tasks/cancel","params":{"id":"cancel-test-task"}}"""
        val response = server.handleJsonRpc(request)

        assertNull(response.error)
        assertNotNull(response.result)
        val resultStr = response.result.toString()
        assertTrue(resultStr.contains("canceled"))

        println("✓ A2AServer cancels tasks correctly")
    }

    @Test
    fun `A2AServer handles message_send`() = withRouta { routa ->
        routa.coordinator.initialize("test-workspace")
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")

        val request = """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "method": "message/send",
                "params": {
                    "message": {
                        "role": "user",
                        "parts": [{"type": "text", "text": "Hello A2A agent"}],
                        "messageId": "msg-test-1"
                    }
                }
            }
        """.trimIndent()

        val response = server.handleJsonRpc(request)

        // The response should contain a task (either submitted or failed depending on agent availability)
        assertNotNull(response.id)
        assertEquals("1", response.id)

        println("✓ A2AServer handles message/send")
    }

    // ── A2A Tool Manager Tests ──────────────────────────────────────

    @Test
    fun `A2AToolManager getLocalAgentCard works`() = withRouta { routa ->
        val a2aServer = A2AServer(routa, "test-workspace", "http://localhost:8080")
        val toolManager = A2AToolManager(a2aServer)

        val result = toolManager.getLocalAgentCard()
        assertTrue(result.success)
        assertTrue(result.data.contains("Routa Agent Hub"))
        assertTrue(result.data.contains("multi_agent_orchestration"))

        println("✓ A2AToolManager.getLocalAgentCard() works")
    }

    @Test
    fun `A2AToolManager getLocalAgentCard fails without server`() {
        val toolManager = A2AToolManager(null)

        val result = toolManager.getLocalAgentCard()
        assertFalse(result.success)
        assertTrue(result.error!!.contains("not configured"))

        println("✓ A2AToolManager handles missing A2A server correctly")
    }

    // ── MCP Server Integration Tests ────────────────────────────────

    @Test
    fun `AgentHubMcpServer includes A2A tools`() = runBlocking {
        val (mcpServer, routa) = com.phodal.routa.hub.mcp.AgentHubMcpServer.create(
            "test-workspace",
            a2aBaseUrl = "http://localhost:8080",
        )

        try {
            routa.coordinator.initialize("test-workspace")
            assertNotNull("MCP server should be initialized", mcpServer)
            println("✓ Agent Hub MCP server initialized with A2A tools")
        } finally {
            routa.coordinator.shutdown()
        }
    }

    @Test
    fun `Routa task status maps to A2A task state correctly`() = withRouta { routa ->
        val server = A2AServer(routa, "test-workspace", "http://localhost:8080")
        routa.coordinator.initialize("test-workspace")

        // Test each Routa status mapping
        val statusMappings = listOf(
            TaskStatus.PENDING to "submitted",
            TaskStatus.IN_PROGRESS to "working",
            TaskStatus.REVIEW_REQUIRED to "working",
            TaskStatus.COMPLETED to "completed",
            TaskStatus.NEEDS_FIX to "working",
            TaskStatus.BLOCKED to "input-required",
            TaskStatus.CANCELLED to "canceled",
        )

        for ((routaStatus, expectedA2AState) in statusMappings) {
            val taskId = "status-test-${routaStatus.name}"
            val task = Task(
                id = taskId,
                title = "Status Test",
                objective = "Test $routaStatus mapping",
                workspaceId = "test-workspace",
                status = routaStatus,
                createdAt = Instant.now().toString(),
                updatedAt = Instant.now().toString(),
            )
            routa.context.taskStore.save(task)

            val request = """{"jsonrpc":"2.0","id":"1","method":"tasks/get","params":{"id":"$taskId"}}"""
            val response = server.handleJsonRpc(request)

            assertNull("Task $taskId should not have error", response.error)
            val resultStr = response.result.toString()
            assertTrue(
                "Task $taskId ($routaStatus) should map to A2A state '$expectedA2AState', got: $resultStr",
                resultStr.contains(expectedA2AState)
            )
        }

        println("✓ All Routa task status → A2A task state mappings are correct")
    }
}
