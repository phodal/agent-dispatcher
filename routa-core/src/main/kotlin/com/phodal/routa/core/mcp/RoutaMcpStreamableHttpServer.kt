package com.phodal.routa.core.mcp

import com.phodal.routa.core.RoutaSystem
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpWebSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * MCP Server supporting both Streamable HTTP (2025-06-18) and WebSocket transports.
 * 
 * This implementation provides:
 * 1. **Streamable HTTP** (new standard, protocol 2025-06-18):
 *    - POST /mcp - Send JSON-RPC messages, get SSE stream or JSON response
 *    - GET /mcp - Open SSE stream for server-initiated messages
 *    - DELETE /mcp - Terminate session
 *    - Session management via Mcp-Session-Id header
 *    - Protocol version via MCP-Protocol-Version header
 * 
 * 2. **WebSocket** (for compatibility):
 *    - ws://host:port/mcp - WebSocket connection
 * 
 * 3. **Legacy SSE** (deprecated, protocol 2024-11-05):
 *    - GET /sse - SSE endpoint
 *    - POST /sse - HTTP POST endpoint
 */
class RoutaMcpStreamableHttpServer(
    private val workspaceId: String,
    private val host: String = "127.0.0.1",
    private val routa: RoutaSystem? = null,
) {
    private var ktorServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var routaSystem: RoutaSystem? = null
    
    // Session management
    private val sessions = ConcurrentHashMap<String, SessionContext>()
    
    var port: Int = 0
        private set
    
    val isRunning: Boolean get() = ktorServer != null
    
    data class SessionContext(
        val sessionId: String,
        val mcpServer: Server,
        val sseChannels: MutableList<Channel<ServerSentEvent>> = mutableListOf()
    )
    
    fun start(): Int {
        if (ktorServer != null) {
            return port
        }

        val allocatedPort = findAvailablePort()
        val (mcpServer, system) = RoutaMcpServer.create(workspaceId, routa)
        this.routaSystem = system

        val engine = embeddedServer(CIO, host = host, port = allocatedPort) {
            install(SSE)
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            // Legacy SSE endpoint (2024-11-05, deprecated)
            // Must be at Application level, not inside routing {}
            mcp {
                mcpServer
            }

            // WebSocket endpoint (for compatibility)
            // Must be at Application level, not inside routing {}
            mcpWebSocket("/mcp") {
                // Each WebSocket connection gets a fresh MCP server sharing the same Routa system
                val (wsMcpServer, _) = RoutaMcpServer.create(workspaceId, system)
                wsMcpServer
            }

            routing {
                // Streamable HTTP endpoint (2025-06-18)
                route("/mcp") {
                    // POST - Send JSON-RPC message
                    post {
                        handleStreamableHttpPost(call)
                    }

                    // GET - Open SSE stream for server messages
                    get {
                        handleStreamableHttpGet(call)
                    }

                    // DELETE - Terminate session
                    delete {
                        handleStreamableHttpDelete(call)
                    }
                }
            }
        }.start(wait = false)

        this.ktorServer = engine
        this.port = allocatedPort

        return port
    }
    
    /**
     * Handle Streamable HTTP POST requests.
     *
     * For now, this is a placeholder that returns a simple response.
     * Full implementation would require:
     * 1. Parsing JSON-RPC messages
     * 2. Routing to MCP Server instance
     * 3. Handling responses (JSON or SSE stream)
     * 4. Session management
     *
     * Note: The MCP SDK's `mcp` and `mcpWebSocket` extensions handle the protocol
     * automatically. For Streamable HTTP (2025-06-18), we would need to implement
     * the protocol manually or wait for SDK support.
     */
    private suspend fun handleStreamableHttpPost(call: ApplicationCall) {
        val protocolVersion = call.request.header("MCP-Protocol-Version") ?: "2025-06-18"
        val sessionId = call.request.header("Mcp-Session-Id")

        // Validate protocol version
        if (protocolVersion !in listOf("2024-11-05", "2025-03-26", "2025-06-18")) {
            call.respond(HttpStatusCode.BadRequest, "Unsupported protocol version: $protocolVersion")
            return
        }

        val jsonRpcMessage = call.receiveText()
        val json = Json.parseToJsonElement(jsonRpcMessage).jsonObject

        // Check if this is initialize request (creates session)
        val method = json["method"]?.jsonPrimitive?.contentOrNull
        val isInitialize = method == "initialize"

        if (!isInitialize && sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing Mcp-Session-Id header")
            return
        }

        // Handle initialize - create new session
        if (isInitialize) {
            val newSessionId = UUID.randomUUID().toString()
            val (newMcpServer, _) = RoutaMcpServer.create(workspaceId, routaSystem)
            sessions[newSessionId] = SessionContext(newSessionId, newMcpServer)

            // Build initialize response
            val requestId = json["id"] ?: JsonPrimitive(1)
            val response = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId)
                putJsonObject("result") {
                    put("protocolVersion", protocolVersion)
                    putJsonObject("capabilities") {
                        putJsonObject("tools") {
                            put("listChanged", false)
                        }
                    }
                    putJsonObject("serverInfo") {
                        put("name", "routa-mcp-server")
                        put("version", "0.1.0")
                    }
                }
            }

            call.response.header("Mcp-Session-Id", newSessionId)
            call.response.header("MCP-Protocol-Version", protocolVersion)
            call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.OK)
            return
        }

        // Handle other requests
        val session = sessions[sessionId]
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return
        }

        // For now, return a simple acknowledgment
        // Full implementation would process the message through the MCP Server
        call.respondText(
            """{"jsonrpc":"2.0","id":${json["id"]},"result":{}}""",
            ContentType.Application.Json,
            HttpStatusCode.Accepted
        )
    }

    /**
     * Handle Streamable HTTP GET requests (SSE stream).
     *
     * This would open an SSE stream for server-initiated messages.
     * For now, it's a placeholder.
     */
    private suspend fun handleStreamableHttpGet(call: ApplicationCall) {
        val sessionId = call.request.header("Mcp-Session-Id")

        if (sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing Mcp-Session-Id header")
            return
        }

        val session = sessions[sessionId]
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return
        }

        // For now, return a simple message
        // Full implementation would open an SSE stream
        call.respondText(
            "Streamable HTTP SSE stream not fully implemented yet. Use WebSocket or Legacy SSE endpoints.",
            ContentType.Text.Plain
        )
    }

    /**
     * Handle Streamable HTTP DELETE requests (terminate session).
     */
    private suspend fun handleStreamableHttpDelete(call: ApplicationCall) {
        val sessionId = call.request.header("Mcp-Session-Id")

        if (sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing Mcp-Session-Id header")
            return
        }

        val removed = sessions.remove(sessionId)
        if (removed != null) {
            call.respond(HttpStatusCode.OK, "Session terminated")
        } else {
            call.respond(HttpStatusCode.NotFound, "Session not found")
        }
    }
    

    
    fun stop() {
        sessions.clear()
        ktorServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        ktorServer = null
        routaSystem = null
        port = 0
    }

    /**
     * Generate MCP config JSON for Streamable HTTP transport.
     */
    fun toStreamableHttpConfigJson(): String {
        require(isRunning) { "Server is not running, call start() first" }
        return """{"mcpServers":{"routa":{"url":"http://$host:$port/mcp","transport":"streamable-http"}}}"""
    }

    /**
     * Generate MCP config JSON for WebSocket transport.
     */
    fun toWebSocketConfigJson(): String {
        require(isRunning) { "Server is not running, call start() first" }
        return """{"mcpServers":{"routa":{"url":"ws://$host:$port/mcp","type":"websocket"}}}"""
    }

    /**
     * Generate MCP config JSON for Legacy SSE transport.
     */
    fun toLegacySseConfigJson(): String {
        require(isRunning) { "Server is not running, call start() first" }
        return """{"mcpServers":{"routa":{"url":"http://$host:$port/sse","type":"sse"}}}"""
    }

    companion object {
        fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

