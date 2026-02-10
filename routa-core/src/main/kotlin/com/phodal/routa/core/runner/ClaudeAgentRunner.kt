package com.phodal.routa.core.runner

import com.phodal.routa.core.model.AgentRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Agent runner that uses Claude Code CLI (`claude -p`) for task execution.
 *
 * Claude Code runs in non-interactive print mode with full tool access
 * (file editing, bash commands, etc.). Each task gets a fresh invocation.
 *
 * Usage:
 * ```kotlin
 * val runner = ClaudeAgentRunner(
 *     claudePath = "/opt/homebrew/bin/claude",
 *     cwd = "/path/to/project",
 * )
 * val output = runner.run(AgentRole.CRAFTER, crafterId, taskPrompt)
 * ```
 */
class ClaudeAgentRunner(
    private val claudePath: String = "claude",
    private val cwd: String = ".",
    private val allowedTools: List<String> = listOf("Bash", "Read", "Edit", "Write", "Glob", "Grep"),
    private val timeoutMinutes: Long = 5,
    private val onOutput: ((String) -> Unit)? = null,
) : AgentRunner {

    override suspend fun run(role: AgentRole, agentId: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val cmdList = mutableListOf(claudePath, "-p", "--output-format", "text")

            // Add allowed tools for auto-approval
            if (allowedTools.isNotEmpty()) {
                cmdList.add("--allowedTools")
                cmdList.addAll(allowedTools)
            }

            onOutput?.invoke("[Claude] Starting: ${cmdList.joinToString(" ")}\n")

            val pb = ProcessBuilder(cmdList).apply {
                directory(File(cwd))
                redirectErrorStream(false)
            }

            val process = pb.start()

            // Write prompt to stdin and close it
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(prompt)
                writer.flush()
            }

            // Read stdout in background
            val stdoutBuilder = StringBuilder()
            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(4096)
                        var n: Int
                        while (reader.read(buffer).also { n = it } != -1) {
                            val text = String(buffer, 0, n)
                            stdoutBuilder.append(text)
                            onOutput?.invoke(text)
                        }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }

            // Read stderr in background
            val stderrBuilder = StringBuilder()
            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            stderrBuilder.appendLine(line)
                        }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }

            // Wait for completion
            val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                onOutput?.invoke("\n[Claude] Timeout after ${timeoutMinutes}m, killed.\n")
            }

            stdoutThread.join(5000)
            stderrThread.join(5000)

            val exitCode = if (finished) process.exitValue() else -1
            val output = stdoutBuilder.toString()

            onOutput?.invoke("\n[Claude] Exit code: $exitCode\n")

            if (stderrBuilder.isNotBlank()) {
                onOutput?.invoke("[Claude stderr] ${stderrBuilder.toString().take(500)}\n")
            }

            output.ifEmpty {
                "[Claude exited with code $exitCode. stderr: ${stderrBuilder.toString().take(500)}]"
            }
        }
}
