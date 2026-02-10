package com.phodal.routa.core.runner

import com.phodal.routa.core.model.AgentRole

/**
 * Composite agent runner that routes different roles to different backends.
 *
 * Default strategy:
 * - **ROUTA** (coordinator): Uses Koog LLM for planning (text output with @@@task blocks)
 * - **CRAFTER** (implementor): Uses ACP to spawn a real coding agent
 * - **GATE** (verifier): Uses ACP agent for verification (can read files, run tests)
 *
 * When no ACP agent is configured, falls back to Koog for all roles.
 */
class CompositeAgentRunner(
    private val koogRunner: AgentRunner,
    private val acpRunner: AgentRunner? = null,
) : AgentRunner {

    override suspend fun run(role: AgentRole, agentId: String, prompt: String): String {
        val runner = when (role) {
            AgentRole.ROUTA -> koogRunner          // Plans via LLM (text output)
            AgentRole.CRAFTER -> acpRunner ?: koogRunner  // Real agent or fallback
            AgentRole.GATE -> acpRunner ?: koogRunner     // Real agent for verification too
        }

        return runner.run(role, agentId, prompt)
    }
}
