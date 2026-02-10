package com.github.phodal.acpmanager.dispatcher

import com.github.phodal.acpmanager.dispatcher.model.*
import kotlinx.serialization.json.*

/**
 * Parses a Master Agent's response into a [DispatchPlan].
 *
 * Supports JSON format:
 * ```json
 * {
 *   "thinking": "...",
 *   "tasks": [
 *     {
 *       "id": "task-1",
 *       "title": "Research best practices",
 *       "description": "...",
 *       "assigned_agent": "researcher",
 *       "parallel_group": 0,
 *       "dependencies": []
 *     }
 *   ],
 *   "max_parallelism": 2
 * }
 * ```
 */
object PlanParser {

    /**
     * Parse plan from a raw response string.
     * Tries to extract JSON from the response (may be wrapped in markdown code blocks).
     */
    fun parse(rawResponse: String): DispatchPlan {
        val jsonStr = extractJson(rawResponse)
        return parseJson(jsonStr)
    }

    /**
     * Extract JSON content from a response that may contain markdown code blocks.
     */
    internal fun extractJson(raw: String): String {
        // Try to find JSON in code blocks first
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?})\\s*\\n?```")
        val match = codeBlockPattern.find(raw)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Try to find raw JSON object
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return raw.substring(jsonStart, jsonEnd + 1)
        }

        throw IllegalArgumentException("No JSON plan found in response")
    }

    /**
     * Parse a JSON string into a DispatchPlan.
     */
    internal fun parseJson(jsonStr: String): DispatchPlan {
        val json = Json.parseToJsonElement(jsonStr).jsonObject

        val thinking = json["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
        val maxParallelism = json["max_parallelism"]?.jsonPrimitive?.intOrNull ?: 1
        val strategyStr = json["strategy"]?.jsonPrimitive?.contentOrNull
        val strategy = when (strategyStr?.uppercase()) {
            "SINGLE_AGENT", "SINGLE" -> ExecutionStrategy.SINGLE_AGENT
            else -> ExecutionStrategy.MULTI_AGENT
        }

        val tasksArray = json["tasks"]?.jsonArray ?: throw IllegalArgumentException("Missing 'tasks' array")

        val tasks = tasksArray.map { taskElement ->
            val taskObj = taskElement.jsonObject
            AgentTask(
                id = taskObj["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Task missing 'id'"),
                title = taskObj["title"]?.jsonPrimitive?.content ?: "Untitled",
                description = taskObj["description"]?.jsonPrimitive?.content ?: "",
                assignedAgent = taskObj["assigned_agent"]?.jsonPrimitive?.contentOrNull,
                parallelGroup = taskObj["parallel_group"]?.jsonPrimitive?.intOrNull,
                dependencies = taskObj["dependencies"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        }

        return DispatchPlan(
            tasks = tasks,
            maxParallelism = maxParallelism,
            thinking = thinking,
            strategy = strategy,
        )
    }

    /**
     * Build the system prompt that instructs the Master Agent to output a structured plan.
     */
    fun buildPlanningPrompt(userInput: String, availableAgents: List<AgentRole>): String {
        val agentList = availableAgents.joinToString("\n") { "  - ${it.id}: ${it.name} (${it.acpAgentKey})" }

        return """You are a Master Agent that breaks down user tasks into a structured execution plan.

Available agents:
$agentList

Analyze the user's request and create a plan with tasks that can be assigned to agents.
Consider which tasks can run in parallel and which have dependencies.

IMPORTANT: Choose the right execution strategy:
- "multi_agent": Use when tasks are independent or parallelizable. Each task can use a different agent.
- "single_agent": Use when tasks are sequential and build on each other's context (e.g., analyze → implement → test). All tasks will use the same agent to preserve conversational context.

Respond ONLY with a JSON object in this exact format:
```json
{
  "thinking": "Your analysis of the task...",
  "strategy": "multi_agent",
  "tasks": [
    {
      "id": "task-1",
      "title": "Short task title",
      "description": "Detailed description of what to do",
      "assigned_agent": "agent-id",
      "parallel_group": 0,
      "dependencies": []
    }
  ],
  "max_parallelism": 2
}
```

Rules:
- Each task should have a unique id (task-1, task-2, etc.)
- Tasks in the same parallel_group can run concurrently
- dependencies is a list of task ids that must complete before this task starts
- assigned_agent should be one of the available agent ids
- max_parallelism indicates how many tasks can run at the same time
- If tasks require shared context (e.g., one task's output feeds the next), use "single_agent" strategy and set dependencies between tasks
- For single_agent strategy, set max_parallelism to 1

User request: $userInput"""
    }
}
