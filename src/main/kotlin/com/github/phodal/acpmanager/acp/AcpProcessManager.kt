package com.github.phodal.acpmanager.acp

import com.github.phodal.acpmanager.config.AcpAgentConfig
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val log = logger<AcpProcessManager>()

/**
 * Manages the lifecycle of ACP agent processes.
 *
 * Provides:
 * - Process reuse: if an agent is already running, return the existing process
 * - Graceful shutdown: SIGTERM first, then SIGKILL after timeout
 * - Process health monitoring: detect crashed processes and restart
 * - Clean shutdown on JVM exit via shutdown hook
 */
class AcpProcessManager private constructor() {

    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdownAll()
        })
    }

    /**
     * Get or create an agent process for the given config.
     *
     * If a healthy process already exists for this agent key, it is reused.
     * Otherwise, a new process is spawned.
     */
    fun getOrCreateProcess(
        agentKey: String,
        config: AcpAgentConfig,
        cwd: String,
    ): ManagedProcess {
        val existing = processes[agentKey]
        if (existing != null && existing.isAlive()) {
            log.info("Reusing existing ACP agent process for '$agentKey' (pid=${existing.pid})")
            return existing
        }

        // Clean up stale entry if present
        if (existing != null) {
            log.info("Previous ACP agent process for '$agentKey' is dead, spawning a new one")
            existing.destroyQuietly()
            processes.remove(agentKey)
        }

        val managed = spawnProcess(agentKey, config, cwd)
        processes[agentKey] = managed
        return managed
    }

    /**
     * Terminate a specific agent process.
     */
    fun terminateProcess(agentKey: String) {
        val managed = processes.remove(agentKey) ?: return
        log.info("Terminating ACP agent process '$agentKey' (pid=${managed.pid})")
        managed.destroy()
    }

    /**
     * Terminate all managed agent processes.
     */
    fun shutdownAll() {
        if (processes.isEmpty()) return
        log.info("Shutting down all ACP agent processes (${processes.size})")
        val keys = processes.keys.toList()
        for (key in keys) {
            terminateProcess(key)
        }
    }

    /**
     * Check if a process is running for the given agent key.
     */
    fun isRunning(agentKey: String): Boolean {
        return processes[agentKey]?.isAlive() == true
    }

    /**
     * Get all currently managed process keys.
     */
    fun getActiveAgents(): Set<String> {
        return processes.entries
            .filter { it.value.isAlive() }
            .map { it.key }
            .toSet()
    }

    /**
     * Get the process info for a given agent key.
     */
    fun getProcessInfo(agentKey: String): ManagedProcess? {
        return processes[agentKey]?.takeIf { it.isAlive() }
    }

    private fun spawnProcess(
        agentKey: String,
        config: AcpAgentConfig,
        cwd: String,
    ): ManagedProcess {
        val cmdList = config.getCommandLine()

        log.info("Spawning ACP agent process: ${cmdList.joinToString(" ")} (cwd=$cwd)")

        val pb = ProcessBuilder(cmdList).apply {
            directory(File(cwd))
            redirectErrorStream(false) // keep stderr separate for debugging
        }

        // Apply environment variables from config
        config.env.forEach { (k, v) ->
            pb.environment()[k] = v
        }

        val process = pb.start()
        log.info("ACP agent '$agentKey' started (pid=${process.pid()})")

        return ManagedProcess(
            agentKey = agentKey,
            process = process,
            command = cmdList,
        )
    }

    companion object {
        @Volatile
        private var instance: AcpProcessManager? = null

        fun getInstance(): AcpProcessManager {
            return instance ?: synchronized(this) {
                instance ?: AcpProcessManager().also { instance = it }
            }
        }
    }
}
