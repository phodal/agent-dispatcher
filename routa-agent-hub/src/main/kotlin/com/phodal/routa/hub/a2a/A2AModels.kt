package com.phodal.routa.hub.a2a

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A2A Protocol data models (Kotlin equivalents).
 *
 * These correspond to the types in the [A2A Protocol specification](https://a2a-protocol.org/).
 * We implement them natively in Kotlin with `kotlinx.serialization` to stay consistent
 * with the rest of the Routa codebase, rather than depending on the Java `io.a2a.spec` classes.
 *
 * @see <a href="https://github.com/a2aproject/a2a-java">A2A Java SDK</a>
 */

// ── Agent Card ──────────────────────────────────────────────────────────

/**
 * Self-describing manifest for an A2A agent.
 *
 * Provides metadata about an agent: identity, capabilities, supported skills,
 * communication methods, and security requirements.
 */
@Serializable
data class A2AAgentCard(
    /** Human-readable name of the agent. */
    val name: String,
    /** Brief description of the agent's purpose. */
    val description: String,
    /** Agent version. */
    val version: String,
    /** Capabilities supported by the agent. */
    val capabilities: A2ACapabilities = A2ACapabilities(),
    /** Supported input modes (e.g., "text", "audio"). */
    val defaultInputModes: List<String> = listOf("text"),
    /** Supported output modes (e.g., "text", "audio"). */
    val defaultOutputModes: List<String> = listOf("text"),
    /** Skills the agent can perform. */
    val skills: List<A2ASkill> = emptyList(),
    /** Protocol interfaces (transport + URL). */
    val supportedInterfaces: List<A2AInterface> = emptyList(),
    /** Optional provider information. */
    val provider: A2AProvider? = null,
    /** Optional documentation URL. */
    val documentationUrl: String? = null,
    /** Optional icon URL. */
    val iconUrl: String? = null,
)

/**
 * Capabilities supported by an A2A agent.
 */
@Serializable
data class A2ACapabilities(
    /** Whether the agent supports streaming responses. */
    val streaming: Boolean = false,
    /** Whether the agent supports push notifications. */
    val pushNotifications: Boolean = false,
)

/**
 * A distinct skill that an agent can perform.
 */
@Serializable
data class A2ASkill(
    /** Unique skill identifier. */
    val id: String,
    /** Human-readable skill name. */
    val name: String,
    /** Description of the skill. */
    val description: String,
    /** Categorization tags for discovery. */
    val tags: List<String> = emptyList(),
    /** Example queries demonstrating usage. */
    val examples: List<String> = emptyList(),
)

/**
 * Protocol interface supported by an agent.
 */
@Serializable
data class A2AInterface(
    /** Protocol binding (e.g., "JSONRPC", "REST"). */
    val protocol: String,
    /** URL endpoint for this interface. */
    val url: String,
)

/**
 * Information about the organization providing the agent.
 */
@Serializable
data class A2AProvider(
    /** Organization name. */
    val organization: String,
    /** Optional URL for the organization. */
    val url: String? = null,
)

// ── Task ────────────────────────────────────────────────────────────────

/**
 * A2A task lifecycle states.
 */
@Serializable
enum class A2ATaskState {
    @SerialName("submitted") SUBMITTED,
    @SerialName("working") WORKING,
    @SerialName("input-required") INPUT_REQUIRED,
    @SerialName("completed") COMPLETED,
    @SerialName("canceled") CANCELED,
    @SerialName("failed") FAILED,
    @SerialName("rejected") REJECTED;

    /** Whether this is a terminal (final) state. */
    fun isFinal(): Boolean = this in setOf(COMPLETED, CANCELED, FAILED, REJECTED)
}

/**
 * Status of an A2A task.
 */
@Serializable
data class A2ATaskStatus(
    /** Current state. */
    val state: A2ATaskState,
    /** Optional status message. */
    val message: A2AMessage? = null,
    /** ISO-8601 timestamp. */
    val timestamp: String? = null,
)

/**
 * An A2A task representing a stateful operation between client and agent.
 */
@Serializable
data class A2ATask(
    /** Unique task identifier. */
    val id: String,
    /** Context identifier for conversation threading. */
    val contextId: String,
    /** Current status. */
    val status: A2ATaskStatus,
    /** Artifacts produced by the agent. */
    val artifacts: List<A2AArtifact> = emptyList(),
    /** Conversation history. */
    val history: List<A2AMessage> = emptyList(),
    /** Arbitrary metadata. */
    val metadata: Map<String, String>? = null,
)

// ── Message ─────────────────────────────────────────────────────────────

/**
 * Role of a message sender in A2A conversations.
 */
@Serializable
enum class A2AMessageRole {
    @SerialName("user") USER,
    @SerialName("agent") AGENT;
}

/**
 * A message in the A2A conversation.
 */
@Serializable
data class A2AMessage(
    /** Sender role. */
    val role: A2AMessageRole,
    /** Message content parts. */
    val parts: List<A2APart>,
    /** Unique message identifier. */
    val messageId: String? = null,
    /** Associated context ID. */
    val contextId: String? = null,
    /** Associated task ID. */
    val taskId: String? = null,
)

/**
 * A content part within an A2A message.
 *
 * Uses a `type` discriminator for polymorphic serialization.
 */
@Serializable
data class A2APart(
    /** Part type: "text", "file", or "data". */
    val type: String = "text",
    /** Text content (when type is "text"). */
    val text: String? = null,
)

// ── Artifact ────────────────────────────────────────────────────────────

/**
 * An artifact produced by an agent during task execution.
 */
@Serializable
data class A2AArtifact(
    /** Artifact content parts. */
    val parts: List<A2APart>,
    /** Optional artifact name. */
    val name: String? = null,
    /** Optional artifact description. */
    val description: String? = null,
    /** Artifact index for ordering. */
    val index: Int? = null,
)

// ── JSON-RPC ────────────────────────────────────────────────────────────

/**
 * JSON-RPC 2.0 request envelope for A2A protocol.
 */
@Serializable
data class A2AJsonRpcRequest(
    /** JSON-RPC version, always "2.0". */
    val jsonrpc: String = "2.0",
    /** Request ID. */
    val id: String,
    /** Method name. */
    val method: String,
    /** Method parameters. */
    val params: kotlinx.serialization.json.JsonElement? = null,
)

/**
 * JSON-RPC 2.0 response envelope for A2A protocol.
 */
@Serializable
data class A2AJsonRpcResponse(
    /** JSON-RPC version, always "2.0". */
    val jsonrpc: String = "2.0",
    /** Request ID. */
    val id: String? = null,
    /** Successful result. */
    val result: kotlinx.serialization.json.JsonElement? = null,
    /** Error object on failure. */
    val error: A2AJsonRpcError? = null,
)

/**
 * JSON-RPC 2.0 error.
 */
@Serializable
data class A2AJsonRpcError(
    /** Error code. */
    val code: Int,
    /** Error message. */
    val message: String,
    /** Additional error data. */
    val data: kotlinx.serialization.json.JsonElement? = null,
)
