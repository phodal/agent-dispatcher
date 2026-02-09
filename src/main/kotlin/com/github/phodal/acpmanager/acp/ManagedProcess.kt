package com.github.phodal.acpmanager.acp

import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.TimeUnit

private val log = logger<ManagedProcess>()

/**
 * Wrapper around a JVM Process with lifecycle helpers.
 */
class ManagedProcess(
    val agentKey: String,
    val process: Process,
    val command: List<String>,
) {
    val pid: Long get() = process.pid()

    val inputStream get() = process.inputStream
    val outputStream get() = process.outputStream
    val errorStream get() = process.errorStream

    fun isAlive(): Boolean = process.isAlive

    /**
     * Gracefully destroy the process (SIGTERM, then force after timeout).
     */
    fun destroy(timeoutMs: Long = 5000) {
        if (!process.isAlive) return
        try {
            process.destroy()
            val exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!exited) {
                log.warn("ACP agent '$agentKey' did not exit gracefully, force-killing")
                process.destroyForcibly()
            }
        } catch (e: Exception) {
            log.warn("Error destroying ACP agent process '$agentKey'", e)
            process.destroyForcibly()
        }
    }

    /**
     * Destroy without logging errors (for shutdown hooks).
     */
    fun destroyQuietly() {
        try {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        } catch (_: Exception) {
        }
    }
}
