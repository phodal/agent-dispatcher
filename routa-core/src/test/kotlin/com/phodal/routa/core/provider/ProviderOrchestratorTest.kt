package com.phodal.routa.core.provider

import com.phodal.routa.core.RoutaFactory
import com.phodal.routa.core.model.AgentRole
import com.phodal.routa.core.model.TaskStatus
import com.phodal.routa.core.runner.OrchestratorPhase
import com.phodal.routa.core.runner.OrchestratorResult
import com.phodal.routa.core.runner.RoutaOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests the orchestration flow using [AgentProvider] (not the legacy AgentRunner).
 *
 * Verifies:
 * - Provider-based routing works end-to-end
 * - Streaming chunks are delivered
 * - Parallel Crafter execution via coroutineScope
 * - Event replay captures all critical events
 */
class ProviderOrchestratorTest {

    /**
     * Mock provider that supports streaming and tracks execution.
     * Acts as both LLM (ROUTA) and coding agent (CRAFTER/GATE).
     */
    private class MockStreamingProvider : AgentProvider {
        val runLog = CopyOnWriteArrayList<Triple<AgentRole, String, Long>>() // role, agentId, threadId

        override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
            return runStreaming(role, agentId, prompt) {}
        }

        override suspend fun runStreaming(
            role: AgentRole,
            agentId: String,
            prompt: String,
            onChunk: (StreamChunk) -> Unit,
        ): String {
            runLog.add(Triple(role, agentId, Thread.currentThread().id))

            onChunk(StreamChunk.Heartbeat())

            val output = when (role) {
                AgentRole.ROUTA -> {
                    onChunk(StreamChunk.Text("Planning..."))
                    """
                    @@@task
                    # Task Alpha
                    ## Objective
                    Implement feature Alpha
                    ## Scope
                    - src/Alpha.kt
                    ## Definition of Done
                    - Alpha works
                    ## Verification
                    - ./gradlew test
                    @@@

                    @@@task
                    # Task Beta
                    ## Objective
                    Implement feature Beta
                    ## Scope
                    - src/Beta.kt
                    ## Definition of Done
                    - Beta works
                    ## Verification
                    - ./gradlew test
                    @@@
                    """.trimIndent()
                }
                AgentRole.CRAFTER -> {
                    onChunk(StreamChunk.Text("Implementing..."))
                    onChunk(StreamChunk.ToolCall("write_file", ToolCallStatus.STARTED))
                    onChunk(StreamChunk.ToolCall("write_file", ToolCallStatus.COMPLETED))
                    "Implementation complete. All tests pass."
                }
                AgentRole.GATE -> {
                    onChunk(StreamChunk.Text("Verifying..."))
                    "### Verification\n- Verdict: ✅ APPROVED\n- All criteria met."
                }
            }

            onChunk(StreamChunk.Completed("end_turn"))
            return output
        }

        override fun capabilities() = ProviderCapabilities(
            name = "MockStreaming",
            supportsStreaming = true,
            supportsInterrupt = true,
            supportsHealthCheck = true,
            supportsFileEditing = true,
            supportsTerminal = true,
            supportsToolCalling = true,
            maxConcurrentAgents = 10,
            priority = 10,
        )
    }

    @Test
    fun `full flow with AgentProvider delivers streaming chunks`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)
        val provider = MockStreamingProvider()
        val streamChunks = CopyOnWriteArrayList<Pair<String, StreamChunk>>()

        val orchestrator = RoutaOrchestrator(
            routa = routa,
            runner = provider,
            workspaceId = "test-provider",
            onStreamChunk = { agentId, chunk -> streamChunks.add(agentId to chunk) },
        )

        try {
            val result = runBlocking { orchestrator.execute("Build features Alpha and Beta") }

            assertTrue("Expected Success", result is OrchestratorResult.Success)
            val success = result as OrchestratorResult.Success
            assertEquals("Should have 2 tasks", 2, success.taskSummaries.size)

            // Verify streaming chunks were received
            assertTrue("Should have stream chunks", streamChunks.isNotEmpty())

            // Check for specific chunk types
            val textChunks = streamChunks.filter { it.second is StreamChunk.Text }
            val toolChunks = streamChunks.filter { it.second is StreamChunk.ToolCall }
            val completedChunks = streamChunks.filter { it.second is StreamChunk.Completed }

            assertTrue("Should have Text chunks", textChunks.isNotEmpty())
            assertTrue("Should have ToolCall chunks", toolChunks.isNotEmpty())
            assertTrue("Should have Completed chunks", completedChunks.isNotEmpty())
        } finally {
            routa.coordinator.shutdown()
        }
    }

    @Test
    fun `provider-based orchestration uses parallel crafter execution`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)
        val provider = MockStreamingProvider()

        val orchestrator = RoutaOrchestrator(
            routa = routa,
            runner = provider,
            workspaceId = "test-parallel",
        )

        try {
            val result = runBlocking { orchestrator.execute("Build Alpha and Beta") }
            assertTrue("Expected Success", result is OrchestratorResult.Success)

            // Check that CRAFTERs ran (2 crafter runs for 2 tasks)
            val crafterRuns = provider.runLog.filter { it.first == AgentRole.CRAFTER }
            assertEquals("Should have 2 CRAFTER runs", 2, crafterRuns.size)

            // Verify all roles were used
            val roles = provider.runLog.map { it.first }.toSet()
            assertEquals("Should use ROUTA, CRAFTER, GATE", setOf(AgentRole.ROUTA, AgentRole.CRAFTER, AgentRole.GATE), roles)
        } finally {
            routa.coordinator.shutdown()
        }
    }

    @Test
    fun `event replay captures all critical events from orchestration`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)
        val provider = MockStreamingProvider()

        val orchestrator = RoutaOrchestrator(
            routa = routa,
            runner = provider,
            workspaceId = "test-events",
        )

        try {
            runBlocking { orchestrator.execute("Build Alpha and Beta") }

            // Replay all critical events
            val events = runBlocking { routa.eventBus.replayAll() }

            assertTrue("Should have critical events", events.isNotEmpty())

            // Should have agent creation events
            val createdEvents = events.filterIsInstance<com.phodal.routa.core.event.AgentEvent.AgentCreated>()
            assertTrue("Should have agent creation events", createdEvents.size >= 3) // routa + 2 crafters + gate

            // Should have task delegation events
            val delegatedEvents = events.filterIsInstance<com.phodal.routa.core.event.AgentEvent.TaskDelegated>()
            assertEquals("Should delegate 2 tasks", 2, delegatedEvents.size)

            // Should have task status change events
            val statusEvents = events.filterIsInstance<com.phodal.routa.core.event.AgentEvent.TaskStatusChanged>()
            assertTrue("Should have task status changes", statusEvents.isNotEmpty())
        } finally {
            routa.coordinator.shutdown()
        }
    }

    @Test
    fun `CapabilityBasedRouter routes correctly in orchestration`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val routa = RoutaFactory.createInMemory(scope)

        // Create separate providers for LLM and ACP roles
        val llmRuns = CopyOnWriteArrayList<AgentRole>()
        val acpRuns = CopyOnWriteArrayList<AgentRole>()

        val llmProvider = object : AgentProvider {
            override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
                llmRuns.add(role)
                return """
                    @@@task
                    # Simple Task
                    ## Objective
                    Do the thing
                    ## Definition of Done
                    - It works
                    ## Verification
                    - ./gradlew test
                    @@@
                """.trimIndent()
            }
            override fun capabilities() = ProviderCapabilities(
                name = "LLM", supportsToolCalling = true, priority = 5,
            )
        }

        val acpProvider = object : AgentProvider {
            override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
                acpRuns.add(role)
                return when (role) {
                    AgentRole.CRAFTER -> "Done. All tests pass."
                    AgentRole.GATE -> "✅ APPROVED"
                    else -> "unexpected"
                }
            }
            override fun capabilities() = ProviderCapabilities(
                name = "ACP",
                supportsFileEditing = true, supportsTerminal = true,
                supportsStreaming = true, priority = 10,
            )
        }

        val router = CapabilityBasedRouter(llmProvider, acpProvider)

        val orchestrator = RoutaOrchestrator(
            routa = routa,
            runner = router,
            workspaceId = "test-routing",
        )

        try {
            val result = runBlocking { orchestrator.execute("Do the thing") }
            assertTrue("Expected Success", result is OrchestratorResult.Success)

            // LLM should handle ROUTA only
            assertEquals("LLM should be called once for ROUTA", 1, llmRuns.size)
            assertEquals(AgentRole.ROUTA, llmRuns[0])

            // ACP should handle CRAFTER and GATE
            assertTrue("ACP should handle CRAFTER", acpRuns.contains(AgentRole.CRAFTER))
            assertTrue("ACP should handle GATE", acpRuns.contains(AgentRole.GATE))
        } finally {
            routa.coordinator.shutdown()
        }
    }
}
