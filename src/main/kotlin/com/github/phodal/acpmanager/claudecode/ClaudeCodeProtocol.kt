package com.github.phodal.acpmanager.claudecode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Claude Code stream-json protocol messages.
 */

// ─── Outgoing (stdin) ──────────────────────────────────────────────

@Serializable
data class ClaudeUserInput(
    val type: String = "user",
    val message: ClaudeInputMessage,
    @SerialName("session_id") val sessionId: String? = null,
)

@Serializable
data class ClaudeInputMessage(
    val role: String = "user",
    val content: List<ClaudeInputContent>,
)

@Serializable
data class ClaudeInputContent(
    val type: String = "text",
    val text: String = "",
)

// ─── Incoming (stdout) ─────────────────────────────────────────────

enum class ClaudeMessageType {
    SYSTEM, ASSISTANT, USER, RESULT, STREAM_EVENT, UNKNOWN;

    companion object {
        fun fromString(type: String?): ClaudeMessageType = when (type) {
            "system" -> SYSTEM
            "assistant" -> ASSISTANT
            "user" -> USER
            "result" -> RESULT
            "stream_event" -> STREAM_EVENT
            else -> UNKNOWN
        }
    }
}

@Serializable
data class ClaudeContent(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: JsonElement? = null,
    @SerialName("is_error") val isError: Boolean? = null,
)

@Serializable
data class ClaudeStreamDelta(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
    val signature: String? = null,
)

@Serializable
data class ClaudeStreamContentBlock(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
)

@Serializable
data class ClaudeStreamEvent(
    val type: String,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: ClaudeStreamContentBlock? = null,
    val delta: ClaudeStreamDelta? = null,
)

data class ClaudeOutputMessage(
    val type: ClaudeMessageType,
    val subtype: String? = null,
    val sessionId: String? = null,
    val content: List<ClaudeContent> = emptyList(),
    val streamEvent: ClaudeStreamEvent? = null,
    val result: String? = null,
    val isError: Boolean = false,
    val rawJson: JsonObject? = null,
)

// ─── JSON Parser ───────────────────────────────────────────────────

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

private fun parseContentArray(element: JsonElement?): List<ClaudeContent> {
    if (element == null) return emptyList()
    return when (element) {
        is JsonPrimitive -> listOf(ClaudeContent(type = "text", text = element.contentOrNull))
        is JsonArray -> element.mapNotNull { item ->
            try { json.decodeFromJsonElement<ClaudeContent>(item) } catch (_: Exception) { null }
        }
        else -> emptyList()
    }
}

private fun parseStreamEvent(eventObj: JsonObject): ClaudeStreamEvent {
    return ClaudeStreamEvent(
        type = eventObj["type"]?.jsonPrimitive?.contentOrNull ?: "",
        index = eventObj["index"]?.jsonPrimitive?.intOrNull,
        contentBlock = eventObj["content_block"]?.let {
            try { json.decodeFromJsonElement<ClaudeStreamContentBlock>(it) } catch (_: Exception) { null }
        },
        delta = eventObj["delta"]?.let {
            try { json.decodeFromJsonElement<ClaudeStreamDelta>(it) } catch (_: Exception) { null }
        },
    )
}

private val protocolLog = com.intellij.openapi.diagnostic.logger<ClaudeOutputMessage>()

fun parseClaudeOutputLine(line: String): ClaudeOutputMessage? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null

    // Log raw JSON for debugging
    protocolLog.info("[ClaudeCode JSON] $trimmed")

    return try {
        val jsonObj = json.parseToJsonElement(trimmed).jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.contentOrNull
        val messageType = ClaudeMessageType.fromString(type)
        val sessionId = jsonObj["session_id"]?.jsonPrimitive?.contentOrNull

        when (messageType) {
            ClaudeMessageType.SYSTEM -> ClaudeOutputMessage(
                type = messageType,
                subtype = jsonObj["subtype"]?.jsonPrimitive?.contentOrNull,
                sessionId = sessionId,
                rawJson = jsonObj,
            )
            ClaudeMessageType.ASSISTANT, ClaudeMessageType.USER -> {
                val messageObj = jsonObj["message"]?.jsonObject
                val contentArray = messageObj?.get("content")
                ClaudeOutputMessage(
                    type = messageType,
                    sessionId = sessionId,
                    content = parseContentArray(contentArray),
                    rawJson = jsonObj,
                )
            }
            ClaudeMessageType.RESULT -> ClaudeOutputMessage(
                type = messageType,
                subtype = jsonObj["subtype"]?.jsonPrimitive?.contentOrNull,
                sessionId = sessionId,
                result = jsonObj["result"]?.jsonPrimitive?.contentOrNull ?: "",
                isError = jsonObj["is_error"]?.jsonPrimitive?.booleanOrNull ?: false,
                rawJson = jsonObj,
            )
            ClaudeMessageType.STREAM_EVENT -> ClaudeOutputMessage(
                type = messageType,
                sessionId = sessionId,
                streamEvent = jsonObj["event"]?.jsonObject?.let { parseStreamEvent(it) },
                rawJson = jsonObj,
            )
            ClaudeMessageType.UNKNOWN -> ClaudeOutputMessage(
                type = messageType,
                sessionId = sessionId,
                rawJson = jsonObj,
            )
        }
    } catch (e: Exception) {
        null
    }
}

// ─── Helpers ───────────────────────────────────────────────────────

fun buildClaudeUserInput(text: String, sessionId: String? = null): String {
    val input = ClaudeUserInput(
        message = ClaudeInputMessage(content = listOf(ClaudeInputContent(text = text))),
        sessionId = sessionId,
    )
    return json.encodeToString(ClaudeUserInput.serializer(), input)
}

fun parseJsonToMap(input: JsonElement?): Map<String, Any> {
    if (input == null || input !is JsonObject) return emptyMap()
    return input.entries.associate { (key, value) ->
        key to when (value) {
            is JsonPrimitive -> value.contentOrNull ?: value.toString()
            is JsonArray -> value.toString()
            is JsonObject -> value.toString()
            else -> value.toString()
        }
    }
}

fun extractToolResultText(content: ClaudeContent): String {
    val c = content.content
    return when (c) {
        is JsonPrimitive -> c.contentOrNull ?: ""
        else -> c?.toString() ?: ""
    }
}

fun mapClaudeToolName(claudeToolName: String): String = when (claudeToolName) {
    "Bash" -> "shell"
    "Read" -> "read-file"
    "Write" -> "write-file"
    "Edit" -> "edit-file"
    "Glob" -> "glob"
    "Grep" -> "grep"
    else -> claudeToolName
}
