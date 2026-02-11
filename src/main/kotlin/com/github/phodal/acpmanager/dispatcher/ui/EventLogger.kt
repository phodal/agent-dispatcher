package com.github.phodal.acpmanager.dispatcher.ui

import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.openapi.diagnostic.logger
import com.phodal.routa.core.provider.StreamChunk
import kotlinx.serialization.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val log = logger<EventLogger>()

/**
 * Logs all RenderEvent and StreamChunk events to a JSONL debug file for troubleshooting.
 *
 * When enabled, creates timestamped log files in ~/.routa/logs/
 * to help diagnose missing messages or rendering issues.
 *
 * Format: One JSON object per line (JSONL).
 */
object EventLogger {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    private var logFile: File? = null
    private var isEnabled = false
    private val json = Json { prettyPrint = false }

    /**
     * Enable logging to file. Creates a new log file with timestamp.
     */
    fun enable() {
        if (isEnabled) return

        try {
            val logDir = File(System.getProperty("user.home"), ".routa/logs").apply {
                mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            logFile = File(logDir, "dispatcher_events_$timestamp.jsonl")

            // Write header
            val header = buildJsonObject {
                put("type", "session_start")
                put("timestamp", dateFormat.format(Date()))
            }
            logFile?.writeText("$header\n")

            isEnabled = true
            log.debug("EventLogger enabled: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            log.warn("Failed to enable EventLogger: ${e.message}", e)
        }
    }

    /**
     * Disable logging.
     */
    fun disable() {
        if (!isEnabled) return

        try {
            val footer = buildJsonObject {
                put("type", "session_end")
                put("timestamp", dateFormat.format(Date()))
            }
            logFile?.appendText("$footer\n")
            isEnabled = false
            logFile = null
            log.debug("EventLogger disabled")
        } catch (e: Exception) {
            log.warn("Failed to disable EventLogger: ${e.message}", e)
        }
    }

    /**
     * Log a RenderEvent as JSONL.
     */
    fun logRenderEvent(agentId: String, event: RenderEvent) {
        if (!isEnabled) return

        try {
            val jsonObj = buildJsonObject {
                put("type", "render_event")
                put("timestamp", dateFormat.format(Date()))
                put("agent_id", agentId)
                put("event_class", event::class.simpleName)

                when (event) {
                    is RenderEvent.MessageStart -> {
                        put("event_type", "message_start")
                    }
                    is RenderEvent.MessageChunk -> {
                        put("event_type", "message_chunk")
                        put("content_length", event.content.length)
                        put("content_preview", event.content.take(100).replace("\n", "\\n"))
                    }
                    is RenderEvent.MessageEnd -> {
                        put("event_type", "message_end")
                        put("total_length", event.fullContent.length)
                        put("content_preview", event.fullContent.take(100).replace("\n", "\\n"))
                    }
                    is RenderEvent.ThinkingStart -> {
                        put("event_type", "thinking_start")
                    }
                    is RenderEvent.ThinkingChunk -> {
                        put("event_type", "thinking_chunk")
                        put("content_length", event.content.length)
                        put("content_preview", event.content.take(100).replace("\n", "\\n"))
                    }
                    is RenderEvent.ThinkingEnd -> {
                        put("event_type", "thinking_end")
                        put("total_length", event.fullContent.length)
                        put("content_preview", event.fullContent.take(100).replace("\n", "\\n"))
                    }
                    is RenderEvent.ToolCallStart -> {
                        put("event_type", "tool_call_start")
                        put("tool_name", event.toolName)
                        put("tool_call_id", event.toolCallId)
                        event.title?.let { put("title", it) }
                    }
                    is RenderEvent.ToolCallUpdate -> {
                        put("event_type", "tool_call_update")
                        put("tool_call_id", event.toolCallId)
                        put("status", event.status.toString())
                    }
                    is RenderEvent.ToolCallParameterUpdate -> {
                        put("event_type", "tool_call_params")
                        put("tool_call_id", event.toolCallId)
                        put("params_preview", event.partialParameters.take(200))
                    }
                    is RenderEvent.ToolCallEnd -> {
                        put("event_type", "tool_call_end")
                        put("tool_call_id", event.toolCallId)
                        put("status", event.status.toString())
                        event.output?.let { put("output_preview", it.take(200)) }
                    }
                    is RenderEvent.Info -> {
                        put("event_type", "info")
                        put("message", event.message)
                    }
                    is RenderEvent.Error -> {
                        put("event_type", "error")
                        put("message", event.message)
                    }
                    is RenderEvent.PromptComplete -> {
                        put("event_type", "prompt_complete")
                        put("stop_reason", event.stopReason)
                    }
                    else -> {
                        put("event_type", "other")
                    }
                }
            }

            logFile?.appendText("$jsonObj\n")
        } catch (e: Exception) {
            // Silently fail to avoid disrupting the UI
        }
    }

    /**
     * Log a StreamChunk as JSONL.
     */
    fun logStreamChunk(agentId: String, chunk: StreamChunk) {
        if (!isEnabled) return

        try {
            val jsonObj = buildJsonObject {
                put("type", "stream_chunk")
                put("timestamp", dateFormat.format(Date()))
                put("agent_id", agentId)
                put("chunk_class", chunk::class.simpleName)

                when (chunk) {
                    is StreamChunk.Text -> {
                        put("chunk_type", "text")
                        put("content_length", chunk.content.length)
                        put("content_preview", chunk.content.take(100).replace("\n", "\\n"))
                    }
                    is StreamChunk.Thinking -> {
                        put("chunk_type", "thinking")
                        put("phase", chunk.phase.toString())
                        put("content_length", chunk.content.length)
                        put("content_preview", chunk.content.take(100).replace("\n", "\\n"))
                    }
                    is StreamChunk.ToolCall -> {
                        put("chunk_type", "tool_call")
                        put("name", chunk.name)
                        put("status", chunk.status.toString())
                        chunk.arguments?.let { put("args_preview", it.take(200)) }
                        chunk.result?.let { put("result_preview", it.take(200)) }
                    }
                    is StreamChunk.Error -> {
                        put("chunk_type", "error")
                        put("message", chunk.message)
                    }
                    is StreamChunk.Completed -> {
                        put("chunk_type", "completed")
                        put("stop_reason", chunk.stopReason)
                    }
                    is StreamChunk.CompletionReport -> {
                        put("chunk_type", "completion_report")
                        put("success", chunk.success)
                        put("summary", chunk.summary)
                        if (chunk.filesModified.isNotEmpty()) {
                            put("files_modified", JsonArray(chunk.filesModified.map { JsonPrimitive(it) }))
                        }
                    }
                    else -> {
                        put("chunk_type", "other")
                    }
                }
            }

            logFile?.appendText("$jsonObj\n")
        } catch (e: Exception) {
            // Silently fail
        }
    }

    /**
     * Log a plain message as JSONL.
     */
    fun log(message: String) {
        if (!isEnabled) return

        try {
            val jsonObj = buildJsonObject {
                put("type", "info")
                put("timestamp", dateFormat.format(Date()))
                put("message", message)
            }
            logFile?.appendText("$jsonObj\n")
        } catch (e: Exception) {
            // Silently fail
        }
    }
}
