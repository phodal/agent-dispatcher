package com.phodal.routa.hub.a2a

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Lightweight A2A protocol client using JSON-RPC 2.0 over HTTP.
 *
 * This client communicates with remote A2A-compatible agents following the
 * [Agent2Agent (A2A) Protocol](https://a2a-protocol.org/). It supports:
 * - **Agent discovery**: Fetch an agent's [A2AAgentCard] from `.well-known/agent.json`
 * - **Send messages**: Send user messages and receive task responses
 * - **Task management**: Query task status, cancel tasks
 *
 * Uses Ktor HTTP client (already in the project) for transport, avoiding
 * heavy Java SDK dependencies (Quarkus, CDI, gRPC).
 *
 * ## Usage
 * ```kotlin
 * val client = A2AClient("http://localhost:10001")
 *
 * // Discover agent capabilities
 * val card = client.fetchAgentCard()
 *
 * // Send a message
 * val task = client.sendMessage("What's the weather in Tokyo?")
 * ```
 *
 * @param agentUrl Base URL of the A2A agent server.
 */
class A2AClient(
    private val agentUrl: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 60_000
        }
    }

    /**
     * Fetch the agent card from the well-known endpoint.
     *
     * A2A agents expose their metadata at `/.well-known/agent.json`.
     *
     * @return The agent's [A2AAgentCard], or null if the endpoint is not available.
     */
    suspend fun fetchAgentCard(): A2AAgentCard? {
        return try {
            val response = httpClient.get("$agentUrl/.well-known/agent.json")
            if (response.status.isSuccess()) {
                json.decodeFromString<A2AAgentCard>(response.bodyAsText())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Send a text message to the A2A agent.
     *
     * Uses the JSON-RPC `message/send` method from the A2A protocol.
     *
     * @param text The user message text.
     * @param contextId Optional context ID for conversation threading.
     * @return The resulting [A2ATask], or null on failure.
     */
    suspend fun sendMessage(text: String, contextId: String? = null): A2ATask? {
        val message = A2AMessage(
            role = A2AMessageRole.USER,
            parts = listOf(A2APart(type = "text", text = text)),
            messageId = UUID.randomUUID().toString(),
            contextId = contextId,
        )

        val params = buildJsonObject {
            put("message", json.encodeToJsonElement(message))
        }

        val response = sendJsonRpc("message/send", params)
        return response?.let { parseTaskFromResult(it) }
    }

    /**
     * Get the current state of a task.
     *
     * @param taskId The task identifier.
     * @return The [A2ATask], or null if not found.
     */
    suspend fun getTask(taskId: String): A2ATask? {
        val params = buildJsonObject {
            put("id", taskId)
        }

        val response = sendJsonRpc("tasks/get", params)
        return response?.let { parseTaskFromResult(it) }
    }

    /**
     * Cancel a running task.
     *
     * @param taskId The task identifier to cancel.
     * @return The cancelled [A2ATask], or null on failure.
     */
    suspend fun cancelTask(taskId: String): A2ATask? {
        val params = buildJsonObject {
            put("id", taskId)
        }

        val response = sendJsonRpc("tasks/cancel", params)
        return response?.let { parseTaskFromResult(it) }
    }

    /**
     * Close the HTTP client and release resources.
     */
    fun close() {
        httpClient.close()
    }

    // ── Internal ─────────────────────────────────────────────────────

    private suspend fun sendJsonRpc(method: String, params: JsonElement): A2AJsonRpcResponse? {
        val request = A2AJsonRpcRequest(
            id = UUID.randomUUID().toString(),
            method = method,
            params = params,
        )

        return try {
            val response = httpClient.post(agentUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }

            if (response.status.isSuccess()) {
                json.decodeFromString<A2AJsonRpcResponse>(response.bodyAsText())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTaskFromResult(response: A2AJsonRpcResponse): A2ATask? {
        val result = response.result ?: return null
        return try {
            json.decodeFromJsonElement<A2ATask>(result)
        } catch (e: Exception) {
            null
        }
    }
}
