package com.github.phodal.acpmanager.acp

import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.model.*
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val log = logger<AcpClientSessionOps>()

/**
 * Internal client session operations used by ACP runtime.
 *
 * Implements the full ClientSessionOperations interface:
 * - session/update notifications and permission requests
 * - fs/read_text_file, fs/write_text_file
 * - terminal/create, terminal/output, terminal/release, terminal/wait_for_exit, terminal/kill
 */
class AcpClientSessionOps(
    private val onSessionUpdate: (SessionUpdate) -> Unit,
    private val onPermissionRequest: (SessionUpdate.ToolCallUpdate, List<PermissionOption>) -> RequestPermissionResponse,
    private val cwd: String = System.getProperty("user.dir") ?: ".",
    private val enableFs: Boolean = true,
    private val enableTerminal: Boolean = true,
) : ClientSessionOperations {

    private data class TerminalSession(
        val id: String,
        val process: Process,
        val outputBuffer: StringBuilder = StringBuilder(),
        val outputByteLimit: Long = Long.MAX_VALUE,
    )

    private val terminals = ConcurrentHashMap<String, TerminalSession>()
    private val terminalIdCounter = AtomicInteger(0)

    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        onSessionUpdate(notification)
    }

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        try {
            val summary = permissions.joinToString { opt -> "${opt.kind}:${opt.name}" }
            log.info("ACP requestPermissions: tool=${toolCall.title ?: "tool"} options=[$summary]")
        } catch (_: Exception) {
        }

        return onPermissionRequest(toolCall, permissions)
    }

    // -- File System Operations --

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?,
    ): ReadTextFileResponse = withContext(Dispatchers.IO) {
        if (!enableFs) {
            throw UnsupportedOperationException("ACP fs.read_text_file is disabled")
        }

        val resolved = resolvePath(path)
        log.info("ACP fs.read_text_file: $resolved (line=$line, limit=$limit)")

        if (!resolved.toFile().exists()) {
            throw IllegalArgumentException("File not found: $resolved")
        }
        if (!resolved.toFile().isFile) {
            throw IllegalArgumentException("Not a file: $resolved")
        }

        val allLines = Files.readAllLines(resolved)
        val startLine = (line?.toInt() ?: 1).coerceAtLeast(1) - 1
        val lineLimit = limit?.toInt() ?: (allLines.size - startLine)

        val content = allLines
            .drop(startLine)
            .take(lineLimit)
            .joinToString("\n")

        ReadTextFileResponse(content = content, _meta = JsonNull)
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse = withContext(Dispatchers.IO) {
        if (!enableFs) {
            throw UnsupportedOperationException("ACP fs.write_text_file is disabled")
        }

        val resolved = resolvePath(path)
        log.info("ACP fs.write_text_file: $resolved (${content.length} chars)")

        resolved.parent?.let { parent ->
            if (!Files.exists(parent)) {
                Files.createDirectories(parent)
            }
        }

        Files.writeString(resolved, content)
        WriteTextFileResponse(_meta = JsonNull)
    }

    // -- Terminal Operations --

    override suspend fun terminalCreate(
        command: String,
        args: List<String>,
        cwd: String?,
        env: List<EnvVariable>,
        outputByteLimit: ULong?,
        _meta: JsonElement?,
    ): CreateTerminalResponse = withContext(Dispatchers.IO) {
        if (!enableTerminal) {
            throw UnsupportedOperationException("ACP terminal.create is disabled")
        }

        val terminalId = "term-${terminalIdCounter.incrementAndGet()}"
        val effectiveCwd = cwd ?: this@AcpClientSessionOps.cwd

        val fullCommand = if (args.isEmpty()) command else "$command ${args.joinToString(" ")}"

        val osName = System.getProperty("os.name").lowercase()
        val cmdList = when {
            osName.contains("win") -> listOf("cmd", "/c", fullCommand)
            else -> {
                val shell = File("/bin/bash").takeIf { it.exists() }?.absolutePath
                    ?: File("/bin/sh").absolutePath
                listOf(shell, "-c", fullCommand)
            }
        }

        log.info("ACP terminal.create: $cmdList (cwd=$effectiveCwd, id=$terminalId)")

        val pb = ProcessBuilder(cmdList)
        pb.directory(File(effectiveCwd))
        pb.redirectErrorStream(true)
        env.forEach { envVar -> pb.environment()[envVar.name] = envVar.value }

        val process = pb.start()

        val session = TerminalSession(
            id = terminalId,
            process = process,
            outputByteLimit = outputByteLimit?.toLong() ?: Long.MAX_VALUE,
        )
        terminals[terminalId] = session

        // Start reading output in background
        Thread({
            try {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    var bytesRead: Int
                    while (reader.read(buffer).also { bytesRead = it } != -1) {
                        val text = String(buffer, 0, bytesRead)
                        synchronized(session.outputBuffer) {
                            if (session.outputBuffer.length < session.outputByteLimit) {
                                session.outputBuffer.append(text)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }, "acp-terminal-$terminalId").apply { isDaemon = true }.start()

        CreateTerminalResponse(terminalId = terminalId, _meta = JsonNull)
    }

    override suspend fun terminalOutput(
        terminalId: String,
        _meta: JsonElement?,
    ): TerminalOutputResponse {
        if (!enableTerminal) {
            throw UnsupportedOperationException("ACP terminal.output is disabled")
        }

        val session = terminals[terminalId]
            ?: throw IllegalArgumentException("Unknown terminal: $terminalId")

        val output: String
        val truncated: Boolean
        synchronized(session.outputBuffer) {
            truncated = session.outputBuffer.length >= session.outputByteLimit
            output = session.outputBuffer.toString()
        }

        val exitStatus = if (!session.process.isAlive) {
            TerminalExitStatus(
                exitCode = session.process.exitValue().toUInt(),
                signal = null,
                _meta = JsonNull
            )
        } else {
            null
        }

        return TerminalOutputResponse(
            output = output,
            truncated = truncated,
            exitStatus = exitStatus,
            _meta = JsonNull
        )
    }

    override suspend fun terminalRelease(
        terminalId: String,
        _meta: JsonElement?,
    ): ReleaseTerminalResponse {
        if (!enableTerminal) {
            throw UnsupportedOperationException("ACP terminal.release is disabled")
        }

        val session = terminals.remove(terminalId)
        if (session != null) {
            log.info("ACP terminal.release: $terminalId")
            if (session.process.isAlive) {
                session.process.destroyForcibly()
            }
        }

        return ReleaseTerminalResponse(_meta = JsonNull)
    }

    override suspend fun terminalWaitForExit(
        terminalId: String,
        _meta: JsonElement?,
    ): WaitForTerminalExitResponse = withContext(Dispatchers.IO) {
        if (!enableTerminal) {
            throw UnsupportedOperationException("ACP terminal.wait_for_exit is disabled")
        }

        val session = terminals[terminalId]
            ?: throw IllegalArgumentException("Unknown terminal: $terminalId")

        log.info("ACP terminal.wait_for_exit: $terminalId")

        val exited = session.process.waitFor(5, TimeUnit.MINUTES)

        if (exited) {
            WaitForTerminalExitResponse(
                exitCode = session.process.exitValue().toUInt(),
                signal = null,
                _meta = JsonNull
            )
        } else {
            WaitForTerminalExitResponse(
                exitCode = null,
                signal = null,
                _meta = JsonNull
            )
        }
    }

    override suspend fun terminalKill(
        terminalId: String,
        _meta: JsonElement?,
    ): KillTerminalCommandResponse {
        if (!enableTerminal) {
            throw UnsupportedOperationException("ACP terminal.kill is disabled")
        }

        val session = terminals[terminalId]
            ?: throw IllegalArgumentException("Unknown terminal: $terminalId")

        log.info("ACP terminal.kill: $terminalId")

        if (session.process.isAlive) {
            session.process.destroyForcibly()
        }

        return KillTerminalCommandResponse(_meta = JsonNull)
    }

    // -- Helpers --

    private fun resolvePath(path: String): Path {
        val p = Path.of(path)
        return if (p.isAbsolute) p else Path.of(cwd, path)
    }

    /**
     * Release all terminal sessions.
     */
    fun releaseAll() {
        terminals.values.forEach { session ->
            try {
                if (session.process.isAlive) {
                    session.process.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        terminals.clear()
    }
}
