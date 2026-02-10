package com.github.phodal.acpmanager.ui.slash

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.components.JBTextArea
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

private val log = logger<CommandCompletionHandler>()

/**
 * Handles / command completion in the chat input area.
 * Detects / triggers, shows popup, manages selection, and handles parameter autocomplete.
 */
class CommandCompletionHandler(
    private val inputArea: JBTextArea,
    private val registry: SlashCommandRegistry,
) {
    private var currentPopup: CommandCompletionPopup? = null
    private var commandStartPos: Int = -1
    private var selectedCommand: SlashCommand? = null
    private var currentParameterIndex: Int = -1
    private var parameterStartPos: Int = -1

    /**
     * Handle text change in the input area.
     */
    fun handleTextChange(text: String, caretPos: Int) {
        // Validate inputs
        if (text.isEmpty() || caretPos < 0 || caretPos > text.length) {
            closePopup()
            return
        }

        // Find the last / before the caret
        val lastSlashPos = text.lastIndexOf('/', caretPos - 1)

        // Check if we're in a command context (/ followed by word characters)
        if (lastSlashPos >= 0 && lastSlashPos < caretPos && lastSlashPos + 1 <= text.length) {
            val afterSlash = text.substring(lastSlashPos + 1, caretPos)

            // Only show popup if we have / followed by word characters or nothing
            if (afterSlash.isEmpty() || afterSlash.all { it.isLetterOrDigit() || it == '_' }) {
                commandStartPos = lastSlashPos
                log.info("CommandCompletionHandler: Found command context, query='$afterSlash'")
                showPopup(afterSlash)
                return
            }
        }

        closePopup()
    }

    /**
     * Show the command completion popup.
     */
    private fun showPopup(query: String) {
        val commands = registry.getCommandsByQuery(query)
        log.info("CommandCompletionHandler: Got ${commands.size} commands for query='$query'")
        if (commands.isEmpty()) {
            log.info("CommandCompletionHandler: No commands, closing popup")
            closePopup()
            return
        }

        // Close existing popup
        currentPopup?.close()

        // Create new popup
        val popup = CommandCompletionPopup(inputArea, commands) { selected ->
            insertCommand(selected)
            closePopup()
        }

        // Position popup below the / symbol (use screen coordinates)
        val caretPos = inputArea.caretPosition
        val rect = try {
            inputArea.modelToView2D(caretPos)?.bounds ?: inputArea.modelToView(caretPos)
        } catch (e: Exception) {
            log.warn("CommandCompletionHandler: modelToView failed for position $caretPos: ${e.message}")
            null
        }

        if (rect != null) {
            val point = Point(rect.x, rect.y + rect.height)
            SwingUtilities.convertPointToScreen(point, inputArea)
            log.info("CommandCompletionHandler: Showing popup at screen coordinates (${point.x}, ${point.y})")
            popup.show(inputArea, point.x, point.y)
        } else {
            log.warn("CommandCompletionHandler: Could not calculate popup position, using fallback")
            popup.show(inputArea, 0, inputArea.height)
        }

        currentPopup = popup
    }

    /**
     * Insert the selected command into the input area.
     */
    private fun insertCommand(command: SlashCommand) {
        val text = inputArea.text
        val caretPos = inputArea.caretPosition

        // Find the command start position
        val lastSlashPos = text.lastIndexOf('/', caretPos - 1)
        if (lastSlashPos < 0) return

        // Replace from / to caret with the command
        val beforeSlash = text.substring(0, lastSlashPos)
        val afterCaret = text.substring(caretPos)
        val newText = beforeSlash + "/" + command.name + " " + afterCaret

        inputArea.text = newText
        inputArea.caretPosition = beforeSlash.length + command.name.length + 2

        // Store selected command and show parameter hints if it has parameters
        selectedCommand = command
        if (command.parameters.isNotEmpty()) {
            currentParameterIndex = 0
            parameterStartPos = inputArea.caretPosition
            showParameterHints(command)
        }
    }

    /**
     * Show parameter hints for the selected command.
     */
    private fun showParameterHints(command: SlashCommand) {
        if (command.parameters.isEmpty()) return

        try {
            val caretPos = inputArea.caretPosition
            val rect = try {
                inputArea.modelToView2D(caretPos)?.bounds ?: inputArea.modelToView(caretPos)
            } catch (e: Exception) {
                log.warn("CommandCompletionHandler: modelToView failed for parameter hints: ${e.message}")
                null
            }

            if (rect != null) {
                val point = Point(rect.x, rect.y + rect.height)
                SwingUtilities.convertPointToScreen(point, inputArea)
                currentPopup?.showParameterHints(command, point.x, point.y)
            }
        } catch (e: Exception) {
            log.debug("Failed to show parameter hints: ${e.message}")
        }
    }

    /**
     * Handle key press in the input area.
     * Returns true if the event was consumed by the popup.
     */
    fun handleKeyPress(e: KeyEvent): Boolean {
        if (currentPopup == null) return false

        return when (e.keyCode) {
            KeyEvent.VK_ESCAPE -> {
                closePopup()
                true
            }
            KeyEvent.VK_TAB -> {
                if (selectedCommand != null && selectedCommand!!.parameters.isNotEmpty()) {
                    handleTabNavigation(e.isShiftDown)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Handle Tab navigation between command parameters.
     */
    private fun handleTabNavigation(isShiftDown: Boolean) {
        val command = selectedCommand ?: return
        if (command.parameters.isEmpty()) return

        // Move to next/previous parameter
        if (isShiftDown) {
            currentParameterIndex = (currentParameterIndex - 1).coerceAtLeast(-1)
        } else {
            currentParameterIndex = (currentParameterIndex + 1).coerceAtMost(command.parameters.size - 1)
        }

        if (currentParameterIndex >= 0 && currentParameterIndex < command.parameters.size) {
            val param = command.parameters[currentParameterIndex]

            // Show autocomplete suggestions for this parameter
            param.autocomplete?.let { autocomplete ->
                val suggestions = autocomplete.invoke()
                if (suggestions.isNotEmpty()) {
                    // Insert first suggestion as placeholder
                    val text = inputArea.text
                    val caretPos = inputArea.caretPosition
                    val newText = text.substring(0, caretPos) + suggestions[0] + " " + text.substring(caretPos)
                    inputArea.text = newText
                    inputArea.caretPosition = caretPos + suggestions[0].length + 1
                }
            }
        }
    }

    /**
     * Close the current popup.
     */
    fun closePopup() {
        currentPopup?.close()
        currentPopup = null
        commandStartPos = -1
        selectedCommand = null
        currentParameterIndex = -1
        parameterStartPos = -1
    }
}

