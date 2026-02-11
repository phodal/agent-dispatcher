package com.phodal.routa.core.provider

import com.phodal.routa.core.model.AgentRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [CapabilityBasedRouter] and the provider selection algorithm.
 */
class CapabilityBasedRouterTest {

    // ── Test providers with different capabilities ───────────────────

    /** Simulates Koog: tool calling, no file editing, no terminal. */
    private class MockLlmProvider : AgentProvider {
        val runLog = mutableListOf<Pair<AgentRole, String>>()

        override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
            runLog.add(role to agentId)
            return "LLM response for $role"
        }

        override fun capabilities() = ProviderCapabilities(
            name = "MockLLM",
            supportsToolCalling = true,
            supportsFileEditing = false,
            supportsTerminal = false,
            maxConcurrentAgents = 10,
            priority = 5,
        )
    }

    /** Simulates ACP: file editing + terminal, no tool calling. */
    private class MockAcpProvider : AgentProvider {
        val runLog = mutableListOf<Pair<AgentRole, String>>()

        override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
            runLog.add(role to agentId)
            return "ACP response for $role"
        }

        override fun capabilities() = ProviderCapabilities(
            name = "MockACP",
            supportsFileEditing = true,
            supportsTerminal = true,
            supportsStreaming = true,
            supportsInterrupt = true,
            supportsHealthCheck = true,
            maxConcurrentAgents = 5,
            priority = 10,
        )
    }

    /** Simulates a provider that can do everything (lower priority). */
    private class MockFullProvider : AgentProvider {
        val runLog = mutableListOf<Pair<AgentRole, String>>()

        override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
            runLog.add(role to agentId)
            return "Full response for $role"
        }

        override fun capabilities() = ProviderCapabilities(
            name = "MockFull",
            supportsToolCalling = true,
            supportsFileEditing = true,
            supportsTerminal = true,
            supportsStreaming = true,
            maxConcurrentAgents = 3,
            priority = 3, // Lower priority than both MockLLM and MockACP
        )
    }

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    fun `ROUTA selects LLM provider (needs tool calling)`() {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val router = CapabilityBasedRouter(llm, acp)

        val selected = router.selectProvider(AgentRole.ROUTA)
        assertEquals("MockLLM", selected.capabilities().name)
    }

    @Test
    fun `CRAFTER selects ACP provider (needs file editing + terminal)`() {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val router = CapabilityBasedRouter(llm, acp)

        val selected = router.selectProvider(AgentRole.CRAFTER)
        assertEquals("MockACP", selected.capabilities().name)
    }

    @Test
    fun `GATE selects ACP provider (needs terminal)`() {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val router = CapabilityBasedRouter(llm, acp)

        val selected = router.selectProvider(AgentRole.GATE)
        assertEquals("MockACP", selected.capabilities().name)
    }

    @Test
    fun `higher priority wins when multiple providers match`() {
        val acp = MockAcpProvider()   // priority = 10
        val full = MockFullProvider() // priority = 3
        val router = CapabilityBasedRouter(full, acp)

        // Both satisfy CRAFTER requirements, but ACP has higher priority
        val selected = router.selectProvider(AgentRole.CRAFTER)
        assertEquals("MockACP", selected.capabilities().name)
    }

    @Test
    fun `run routes to correct provider per role`() = runBlocking {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val router = CapabilityBasedRouter(llm, acp)

        router.run(AgentRole.ROUTA, "routa-1", "plan")
        router.run(AgentRole.CRAFTER, "crafter-1", "implement")
        router.run(AgentRole.GATE, "gate-1", "verify")

        assertEquals("LLM should handle ROUTA", 1, llm.runLog.size)
        assertEquals(AgentRole.ROUTA, llm.runLog[0].first)

        assertEquals("ACP should handle CRAFTER and GATE", 2, acp.runLog.size)
        assertEquals(AgentRole.CRAFTER, acp.runLog[0].first)
        assertEquals(AgentRole.GATE, acp.runLog[1].first)
    }

    @Test(expected = NoSuitableProviderException::class)
    fun `throws when no provider satisfies requirements`() {
        // Only LLM provider — can't do file editing for CRAFTER
        val llm = MockLlmProvider()
        val router = CapabilityBasedRouter(llm)

        router.selectProvider(AgentRole.CRAFTER) // Should throw
    }

    @Test
    fun `dynamic registration adds provider at runtime`() {
        val llm = MockLlmProvider()
        val router = CapabilityBasedRouter(llm)

        // Initially, CRAFTER has no provider
        try {
            router.selectProvider(AgentRole.CRAFTER)
            fail("Should throw NoSuitableProviderException")
        } catch (_: NoSuitableProviderException) { /* expected */ }

        // Register ACP at runtime
        val acp = MockAcpProvider()
        router.register(acp)

        // Now CRAFTER should work
        val selected = router.selectProvider(AgentRole.CRAFTER)
        assertEquals("MockACP", selected.capabilities().name)
    }

    @Test
    fun `unregister removes provider`() {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val router = CapabilityBasedRouter(llm, acp)

        assertEquals(2, router.listProviders().size)

        router.unregister("MockACP")
        assertEquals(1, router.listProviders().size)
        assertEquals("MockLLM", router.listProviders()[0].name)
    }

    @Test
    fun `listProviders returns all registered providers`() {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val full = MockFullProvider()
        val router = CapabilityBasedRouter(llm, acp, full)

        val providers = router.listProviders()
        assertEquals(3, providers.size)
        assertTrue(providers.any { it.name == "MockLLM" })
        assertTrue(providers.any { it.name == "MockACP" })
        assertTrue(providers.any { it.name == "MockFull" })
    }

    @Test
    fun `router capabilities are union of all providers`() {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val router = CapabilityBasedRouter(llm, acp)

        val caps = router.capabilities()
        assertTrue("Union should support tool calling (from LLM)", caps.supportsToolCalling)
        assertTrue("Union should support file editing (from ACP)", caps.supportsFileEditing)
        assertTrue("Union should support terminal (from ACP)", caps.supportsTerminal)
        assertTrue("Union should support streaming (from ACP)", caps.supportsStreaming)
        assertEquals("Max concurrent = 10 + 5", 15, caps.maxConcurrentAgents)
        assertEquals("Priority = max(5, 10)", 10, caps.priority)
    }

    @Test
    fun `ProviderRequirements satisfies check works correctly`() {
        val acpCaps = ProviderCapabilities(
            name = "test",
            supportsFileEditing = true,
            supportsTerminal = true,
        )

        // CRAFTER needs file editing + terminal
        val crafterReqs = ProviderRequirements(needsFileEditing = true, needsTerminal = true)
        assertTrue("ACP satisfies CRAFTER requirements", acpCaps.satisfies(crafterReqs))

        // GATE needs terminal
        val gateReqs = ProviderRequirements(needsTerminal = true)
        assertTrue("ACP satisfies GATE requirements", acpCaps.satisfies(gateReqs))

        // ROUTA needs tool calling — ACP doesn't have it
        val routaReqs = ProviderRequirements(needsToolCalling = true)
        assertFalse("ACP does NOT satisfy ROUTA requirements (no tool calling)",
            acpCaps.satisfies(routaReqs))
    }

    @Test
    fun `fallback to full provider when specialized one is removed`() = runBlocking {
        val llm = MockLlmProvider()
        val acp = MockAcpProvider()
        val full = MockFullProvider()
        val router = CapabilityBasedRouter(llm, acp, full)

        // ACP handles CRAFTER with higher priority
        assertEquals("MockACP", router.selectProvider(AgentRole.CRAFTER).capabilities().name)

        // Remove ACP
        router.unregister("MockACP")

        // Full provider should take over (it has file editing + terminal)
        assertEquals("MockFull", router.selectProvider(AgentRole.CRAFTER).capabilities().name)
    }
}
