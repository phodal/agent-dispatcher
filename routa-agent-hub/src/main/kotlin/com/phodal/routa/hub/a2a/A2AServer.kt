package com.phodal.routa.hub.a2a

import com.phodal.routa.core.RoutaSystem
import com.phodal.routa.core.model.AgentStatus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

/**
 * A2A protocol server that exposes Routa agents as A2A-compatible endpoints.
 *
 * This server implements the [Agent2Agent (A2A) Protocol](https://a2a-protocol.org/)
 * using Ktor, allowing external A2A clients to discover and communicate with Routa agents.
 *
 * Supported endpoints:
 * - `GET  /.well-known/agent.json` — Agent card discovery
 * - `POST /` — JSON-RPC 2.0 for `message/send`, `tasks/get`, `tasks/cancel`
 *
 * ## Usage
 * ```kotlin
 * // In Ktor routing:
 * val a2aServer = A2AServer(routaSystem, workspaceId, "http://localhost:8080")
 * routing {
 *     a2aServer.registerRoutes(this)
 * }
 * ```
 *
 * @param system The Routa system with stores and coordinator.
 * @param workspaceId The workspace to expose via A2A.
 * @param baseUrl The public URL of this server (for the agent card).
 */
class A2AServer(
    private val system: RoutaSystem,
    private val workspaceId: String,
    private val baseUrl: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Register A2A protocol routes with a Ktor routing scope.
     */
    fun registerRoutes(routing: Routing) {
        routing.get("/.well-known/agent.json") {
            val card = buildAgentCard()
            call.respondText(json.encodeToString(card), ContentType.Application.Json)
        }

        routing.post("/") {
            val body = call.receiveText()
            val response = handleJsonRpc(body)
            call.respondText(json.encodeToString(response), ContentType.Application.Json)
        }
    }

    /**
     * Build the A2A agent card describing this Routa hub's capabilities.
     */
    fun buildAgentCard(): A2AAgentCard {
        val skills = buildList {
            add(
                A2ASkill(
                    id = "multi_agent_orchestration",
                    name = "Multi-Agent Orchestration",
                    description = "Orchestrate complex tasks using specialized agents: " +
                        "ROUTA (coordinator), CRAFTER (implementor), GATE (verifier)",
                    tags = listOf("orchestration", "multi-agent", "coordination"),
                    examples = listOf(
                        "Create a CRAFTER agent to implement the auth module",
                        "Delegate task-1 to agent-abc",
                    ),
                )
            )
            add(
                A2ASkill(
                    id = "agent_management",
                    name = "Agent Lifecycle Management",
                    description = "Create, monitor, and manage agents. " +
                        "Track status, read conversations, send messages between agents.",
                    tags = listOf("agents", "lifecycle", "management"),
                    examples = listOf(
                        "List all agents in the workspace",
                        "Get the status of agent-xyz",
                    ),
                )
            )
            add(
                A2ASkill(
                    id = "task_delegation",
                    name = "Task Delegation",
                    description = "Delegate tasks to agents, track progress, " +
                        "and collect completion reports.",
                    tags = listOf("tasks", "delegation", "tracking"),
                    examples = listOf(
                        "Create a task and assign it to a crafter",
                        "Report task completion to the parent agent",
                    ),
                )
            )
        }

        return A2AAgentCard(
            name = "Routa Agent Hub",
            description = "A multi-agent orchestration hub providing agent lifecycle management, " +
                "task delegation, and coordination capabilities via the A2A protocol.",
            version = "0.1.0",
            capabilities = A2ACapabilities(
                streaming = false,
                pushNotifications = false,
            ),
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text"),
            skills = skills,
            supportedInterfaces = listOf(
                A2AInterface(protocol = "JSONRPC", url = baseUrl),
            ),
            provider = A2AProvider(organization = "Routa"),
        )
    }

    // ── JSON-RPC Handler ─────────────────────────────────────────────

    internal fun handleJsonRpc(body: String): A2AJsonRpcResponse {
        val request = try {
            json.decodeFromString<A2AJsonRpcRequest>(body)
        } catch (e: Exception) {
            return A2AJsonRpcResponse(
                error = A2AJsonRpcError(
                    code = -32700,
                    message = "Parse error: ${e.message}",
                )
            )
        }

        return try {
            when (request.method) {
                "message/send" -> handleSendMessage(request)
                "tasks/get" -> handleGetTask(request)
                "tasks/cancel" -> handleCancelTask(request)
                else -> A2AJsonRpcResponse(
                    id = request.id,
                    error = A2AJsonRpcError(
                        code = -32601,
                        message = "Method not found: ${request.method}",
                    )
                )
            }
        } catch (e: Exception) {
            A2AJsonRpcResponse(
                id = request.id,
                error = A2AJsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}",
                )
            )
        }
    }

    private fun handleSendMessage(request: A2AJsonRpcRequest): A2AJsonRpcResponse {
        val params = request.params?.jsonObject ?: return errorResponse(request.id, "Missing params")
        val messageJson = params["message"] ?: return errorResponse(request.id, "Missing message")

        val message = try {
            json.decodeFromJsonElement<A2AMessage>(messageJson)
        } catch (e: Exception) {
            return errorResponse(request.id, "Invalid message: ${e.message}")
        }

        // Extract text from message parts
        val text = message.parts
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("\n")

        if (text.isBlank()) {
            return errorResponse(request.id, "Empty message text")
        }

        // Create a task ID and context ID
        val taskId = "a2a-${UUID.randomUUID()}"
        val contextId = message.contextId ?: "ctx-${UUID.randomUUID()}"

        // Delegate to the Routa system by creating a message for the coordinator
        val routaId = runBlocking { findOrCreateRoutaAgent() }

        // Send the message via Routa tools
        val result = runBlocking {
            system.tools.messageAgent(
                fromAgentId = "a2a-client",
                toAgentId = routaId,
                message = text,
            )
        }

        val state = if (result.success) A2ATaskState.SUBMITTED else A2ATaskState.FAILED
        val task = A2ATask(
            id = taskId,
            contextId = contextId,
            status = A2ATaskStatus(
                state = state,
                message = A2AMessage(
                    role = A2AMessageRole.AGENT,
                    parts = listOf(A2APart(text = if (result.success) result.data else (result.error ?: "Error"))),
                ),
                timestamp = Instant.now().toString(),
            ),
        )

        return A2AJsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(task),
        )
    }

    private fun handleGetTask(request: A2AJsonRpcRequest): A2AJsonRpcResponse {
        val params = request.params?.jsonObject ?: return errorResponse(request.id, "Missing params")
        val taskId = params["id"]?.jsonPrimitive?.contentOrNull ?: return errorResponse(request.id, "Missing task id")

        // Look up the task in Routa's task store
        val routaTask = runBlocking { system.context.taskStore.get(taskId) }

        if (routaTask != null) {
            val a2aTask = routaTaskToA2ATask(routaTask)
            return A2AJsonRpcResponse(
                id = request.id,
                result = json.encodeToJsonElement(a2aTask),
            )
        }

        return errorResponse(request.id, "Task not found: $taskId")
    }

    private fun handleCancelTask(request: A2AJsonRpcRequest): A2AJsonRpcResponse {
        val params = request.params?.jsonObject ?: return errorResponse(request.id, "Missing params")
        val taskId = params["id"]?.jsonPrimitive?.contentOrNull ?: return errorResponse(request.id, "Missing task id")

        val routaTask = runBlocking { system.context.taskStore.get(taskId) }

        if (routaTask != null) {
            val updatedTask = routaTask.copy(
                status = com.phodal.routa.core.model.TaskStatus.CANCELLED,
                updatedAt = Instant.now().toString(),
            )
            runBlocking { system.context.taskStore.save(updatedTask) }

            val a2aTask = routaTaskToA2ATask(updatedTask)
            return A2AJsonRpcResponse(
                id = request.id,
                result = json.encodeToJsonElement(a2aTask),
            )
        }

        return errorResponse(request.id, "Task not found: $taskId")
    }

    // ── Mapping Helpers ──────────────────────────────────────────────

    private fun routaTaskToA2ATask(task: com.phodal.routa.core.model.Task): A2ATask {
        val state = when (task.status) {
            com.phodal.routa.core.model.TaskStatus.PENDING -> A2ATaskState.SUBMITTED
            com.phodal.routa.core.model.TaskStatus.IN_PROGRESS -> A2ATaskState.WORKING
            com.phodal.routa.core.model.TaskStatus.REVIEW_REQUIRED -> A2ATaskState.WORKING
            com.phodal.routa.core.model.TaskStatus.COMPLETED -> A2ATaskState.COMPLETED
            com.phodal.routa.core.model.TaskStatus.NEEDS_FIX -> A2ATaskState.WORKING
            com.phodal.routa.core.model.TaskStatus.BLOCKED -> A2ATaskState.INPUT_REQUIRED
            com.phodal.routa.core.model.TaskStatus.CANCELLED -> A2ATaskState.CANCELED
        }

        val artifacts = if (task.completionSummary != null) {
            listOf(
                A2AArtifact(
                    parts = listOf(A2APart(text = task.completionSummary)),
                    name = "completion_summary",
                )
            )
        } else emptyList()

        return A2ATask(
            id = task.id,
            contextId = task.workspaceId,
            status = A2ATaskStatus(
                state = state,
                timestamp = task.updatedAt,
            ),
            artifacts = artifacts,
        )
    }

    private suspend fun findOrCreateRoutaAgent(): String {
        // Find the first ROUTA agent in the workspace
        val agents = system.context.agentStore.listByWorkspace(workspaceId)
        val routa = agents.find { agent ->
            agent.role == com.phodal.routa.core.model.AgentRole.ROUTA &&
                agent.status in setOf(AgentStatus.PENDING, AgentStatus.ACTIVE)
        }
        return routa?.id ?: agents.firstOrNull()?.id ?: "routa-default"
    }

    private fun errorResponse(requestId: String, message: String): A2AJsonRpcResponse {
        return A2AJsonRpcResponse(
            id = requestId,
            error = A2AJsonRpcError(code = -32602, message = message),
        )
    }
}
