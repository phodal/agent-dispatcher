package com.github.phodal.acpmanager.dispatcher

import com.github.phodal.acpmanager.dispatcher.model.AgentRole
import com.github.phodal.acpmanager.dispatcher.model.ExecutionStrategy
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [PlanParser] — parsing Master Agent responses into DispatchPlan.
 */
class PlanParserTest {

    @Test
    fun `test parse simple JSON plan`() {
        val json = """
        {
            "thinking": "Need to implement auth module",
            "tasks": [
                {
                    "id": "task-1",
                    "title": "Research JWT",
                    "description": "Research best practices for JWT auth",
                    "assigned_agent": "researcher",
                    "parallel_group": 0,
                    "dependencies": []
                },
                {
                    "id": "task-2",
                    "title": "Implement auth",
                    "description": "Implement the auth middleware",
                    "assigned_agent": "coder",
                    "parallel_group": 0,
                    "dependencies": []
                },
                {
                    "id": "task-3",
                    "title": "Code review",
                    "description": "Review the auth implementation",
                    "assigned_agent": "reviewer",
                    "parallel_group": 1,
                    "dependencies": ["task-1", "task-2"]
                }
            ],
            "max_parallelism": 2
        }
        """.trimIndent()

        val plan = PlanParser.parse(json)

        assertEquals("Need to implement auth module", plan.thinking)
        assertEquals(3, plan.tasks.size)
        assertEquals(2, plan.maxParallelism)

        val task1 = plan.tasks[0]
        assertEquals("task-1", task1.id)
        assertEquals("Research JWT", task1.title)
        assertEquals("researcher", task1.assignedAgent)
        assertEquals(0, task1.parallelGroup)
        assertTrue(task1.dependencies.isEmpty())

        val task3 = plan.tasks[2]
        assertEquals(listOf("task-1", "task-2"), task3.dependencies)
    }

    @Test
    fun `test parse JSON in markdown code block`() {
        val response = """
        Here's the plan:
        
        ```json
        {
            "thinking": "Simple plan",
            "tasks": [
                {
                    "id": "task-1",
                    "title": "Do something",
                    "description": "Do something detailed"
                }
            ],
            "max_parallelism": 1
        }
        ```
        
        Let me know if you want changes.
        """.trimIndent()

        val plan = PlanParser.parse(response)
        assertEquals(1, plan.tasks.size)
        assertEquals("task-1", plan.tasks[0].id)
    }

    @Test
    fun `test parse JSON with missing optional fields`() {
        val json = """
        {
            "tasks": [
                {
                    "id": "task-1",
                    "title": "Simple task",
                    "description": "Just do it"
                }
            ]
        }
        """.trimIndent()

        val plan = PlanParser.parse(json)
        assertEquals(1, plan.tasks.size)
        assertEquals("", plan.thinking)
        assertEquals(1, plan.maxParallelism)
        assertNull(plan.tasks[0].assignedAgent)
        assertNull(plan.tasks[0].parallelGroup)
        assertTrue(plan.tasks[0].dependencies.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse invalid JSON throws exception`() {
        PlanParser.parse("not json at all")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse JSON without tasks throws exception`() {
        PlanParser.parse("""{"thinking": "no tasks"}""")
    }

    @Test
    fun `test extractJson from raw JSON`() {
        val raw = """{"tasks": []}"""
        val extracted = PlanParser.extractJson(raw)
        assertEquals(raw, extracted)
    }

    @Test
    fun `test extractJson from markdown block`() {
        val raw = """
        Some text
        ```json
        {"tasks": []}
        ```
        More text
        """.trimIndent()
        val extracted = PlanParser.extractJson(raw)
        assertEquals("""{"tasks": []}""", extracted)
    }

    @Test
    fun `test buildPlanningPrompt contains agent info`() {
        val agents = listOf(
            AgentRole(id = "coder", name = "Coder", acpAgentKey = "claude"),
            AgentRole(id = "reviewer", name = "Reviewer", acpAgentKey = "codex"),
        )

        val prompt = PlanParser.buildPlanningPrompt("Implement login", agents)

        assertTrue(prompt.contains("coder"))
        assertTrue(prompt.contains("reviewer"))
        assertTrue(prompt.contains("Implement login"))
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("single_agent"))
        assertTrue(prompt.contains("multi_agent"))
    }

    @Test
    fun `test parse plan with single_agent strategy`() {
        val json = """
        {
            "thinking": "Sequential workflow needs context continuity",
            "strategy": "single_agent",
            "tasks": [
                {"id": "task-1", "title": "Analyze", "description": "Analyze code"},
                {"id": "task-2", "title": "Implement", "description": "Implement fix", "dependencies": ["task-1"]}
            ],
            "max_parallelism": 1
        }
        """.trimIndent()

        val plan = PlanParser.parse(json)
        assertEquals(ExecutionStrategy.SINGLE_AGENT, plan.strategy)
        assertEquals(2, plan.tasks.size)
    }

    @Test
    fun `test parse plan with multi_agent strategy`() {
        val json = """
        {
            "strategy": "multi_agent",
            "tasks": [
                {"id": "task-1", "title": "T1", "description": "D1"}
            ]
        }
        """.trimIndent()

        val plan = PlanParser.parse(json)
        assertEquals(ExecutionStrategy.MULTI_AGENT, plan.strategy)
    }

    @Test
    fun `test parse plan without strategy defaults to multi_agent`() {
        val json = """
        {
            "tasks": [
                {"id": "task-1", "title": "T1", "description": "D1"}
            ]
        }
        """.trimIndent()

        val plan = PlanParser.parse(json)
        assertEquals(ExecutionStrategy.MULTI_AGENT, plan.strategy)
    }
}
