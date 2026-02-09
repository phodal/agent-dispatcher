package com.github.phodal.acpmanager.claudecode

import com.github.phodal.acpmanager.ui.renderer.RenderEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import java.io.File

/**
 * E2E tests for Claude Code client.
 *
 * These tests require the Claude Code binary to be installed.
 * Tests will be skipped if the binary is not found.
 */
class ClaudeCodeE2ETest : BasePlatformTestCase() {

    private val claudeBinaryPaths = listOf(
        "/opt/homebrew/bin/claude",
        "/usr/local/bin/claude",
        System.getenv("HOME") + "/.local/bin/claude",
        "claude" // Try PATH
    )

    private fun findClaudeBinary(): String? {
        for (path in claudeBinaryPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return path
            }
        }
        // Try which command
        return try {
            val proc = ProcessBuilder("which", "claude")
                .redirectErrorStream(true)
                .start()
            val result = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (proc.exitValue() == 0 && result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }

    fun testClaudeCodeClientBasicPrompt() {
        val binaryPath = findClaudeBinary()
        if (binaryPath == null) {
            println("SKIPPED: Claude binary not found. Install Claude Code to run this test.")
            return
        }

        val workingDir = System.getProperty("user.dir")
        val textRenderer = TextRenderer("test-claude")
        val collectedEvents = mutableListOf<RenderEvent>()

        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val client = ClaudeCodeClient(
                scope = scope,
                binaryPath = binaryPath,
                workingDirectory = workingDir,
                permissionMode = "bypassPermissions"
            )

            try {
                // Collect events in background
                val collectJob = scope.launch {
                    client.renderEvents
                        .takeWhile { it !is RenderEvent.PromptComplete && it !is RenderEvent.Disconnected }
                        .toList(collectedEvents)
                }

                // Start client
                client.start()
                assertTrue("Client should be connected", client.isConnected)

                // Send a simple prompt
                withTimeout(60_000) {
                    client.sendPrompt("Say 'Hello from Claude Code test' and nothing else.")
                }

                // Wait for collection to complete
                delay(500)
                collectJob.cancel()

                // Forward events to text renderer
                collectedEvents.forEach { textRenderer.onEvent(it) }

                // Verify events
                val summary = textRenderer.getSummary()
                println("=== Test Output ===")
                println(textRenderer.capturedOutput)
                println("=== Summary ===")
                println("User messages: ${summary.userMessages}")
                println("Assistant messages: ${summary.assistantMessages.size}")
                println("Tool calls: ${summary.toolCalls}")
                println("Errors: ${summary.errors}")
                println("Connected: ${summary.isConnected}")

                // Assertions
                assertTrue("Should have connected event", summary.isConnected)
                assertTrue("Should have user message", summary.userMessages.isNotEmpty())

            } finally {
                client.stop()
                scope.cancel()
            }
        }
    }

    fun testTextRendererEventCapture() {
        // Unit test for TextRenderer without Claude binary
        val renderer = TextRenderer("test")

        renderer.onEvent(RenderEvent.Connected("test-agent"))
        renderer.onEvent(RenderEvent.UserMessage("Hello"))
        renderer.onEvent(RenderEvent.MessageStart())
        renderer.onEvent(RenderEvent.MessageChunk("Hi "))
        renderer.onEvent(RenderEvent.MessageChunk("there!"))
        renderer.onEvent(RenderEvent.MessageEnd("Hi there!"))
        renderer.onEvent(RenderEvent.PromptComplete("end_turn"))

        val summary = renderer.getSummary()

        assertEquals(1, summary.userMessages.size)
        assertEquals("Hello", summary.userMessages[0])
        assertEquals(1, summary.assistantMessages.size)
        assertEquals("Hi there!", summary.assistantMessages[0])
        assertTrue(summary.isConnected)
        assertTrue(summary.isComplete)

        println("TextRenderer output:")
        println(renderer.capturedOutput)
    }

    fun testTextRendererToolCallCapture() {
        val renderer = TextRenderer("test")

        renderer.onEvent(RenderEvent.ToolCallStart(
            toolCallId = "tool-1",
            toolName = "ReadFile",
            title = "Read: test.txt",
            kind = "Read"
        ))
        renderer.onEvent(RenderEvent.ToolCallEnd(
            toolCallId = "tool-1",
            status = com.agentclientprotocol.model.ToolCallStatus.COMPLETED,
            title = "Read: test.txt",
            output = "file content here"
        ))

        val summary = renderer.getSummary()

        assertEquals(1, summary.toolCalls.size)
        assertEquals("ReadFile", summary.toolCalls[0].first)
        assertEquals(1, summary.toolResults.size)
        assertEquals(com.agentclientprotocol.model.ToolCallStatus.COMPLETED, summary.toolResults[0].second)
    }
}

