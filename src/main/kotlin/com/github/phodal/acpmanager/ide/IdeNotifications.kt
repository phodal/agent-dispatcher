package com.github.phodal.acpmanager.ide

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

private val log = logger<IdeNotifications>()

/**
 * Manages outbound notifications from the IDE to connected agents.
 *
 * Mirrors Claude Code's NotificationManager — broadcasts IDE events
 * (selection changes, diagnostics updates, @ mentions) to all
 * registered notification listeners.
 */
class IdeNotifications(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    /**
     * Listener that receives IDE notifications.
     * Agents/sessions can register to receive these events.
     */
    fun interface NotificationListener {
        fun onNotification(notification: IdeNotification)
    }

    private val listeners = CopyOnWriteArrayList<NotificationListener>()

    /**
     * Register a listener to receive IDE notifications.
     */
    fun addListener(listener: NotificationListener) {
        listeners.add(listener)
    }

    /**
     * Remove a previously registered listener.
     */
    fun removeListener(listener: NotificationListener) {
        listeners.remove(listener)
    }

    /**
     * Broadcast a notification to all registered listeners.
     */
    fun broadcastNotification(notification: IdeNotification) {
        if (listeners.isEmpty()) return

        scope.launch(Dispatchers.Default) {
            for (listener in listeners) {
                try {
                    listener.onNotification(notification)
                } catch (e: Exception) {
                    log.warn("Error sending notification ${notification.method}: ${e.message}")
                }
            }
        }
    }

    /**
     * Send a selection_changed notification.
     */
    fun sendSelectionChanged(
        filePath: String?,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        selectedText: String?,
        cursorOffset: Int = 0,
        fileType: String? = null,
    ) {
        broadcastNotification(
            IdeNotification.SelectionChanged(
                filePath = filePath,
                startLine = startLine,
                startColumn = startColumn,
                endLine = endLine,
                endColumn = endColumn,
                selectedText = selectedText,
                cursorOffset = cursorOffset,
                fileType = fileType,
            )
        )
    }

    /**
     * Send an at_mentioned notification.
     */
    fun sendAtMentioned(filePath: String, startLine: Int? = null, endLine: Int? = null) {
        broadcastNotification(
            IdeNotification.AtMentioned(
                filePath = filePath,
                startLine = startLine,
                endLine = endLine,
            )
        )
    }

    /**
     * Send a diagnostics_changed notification.
     */
    fun sendDiagnosticsChanged(uri: String) {
        broadcastNotification(IdeNotification.DiagnosticsChanged(uri))
    }

    /**
     * Capture current editor context (file path and selection range).
     * Returns null if no editor is currently active.
     */
    fun captureEditorContext(): EditorContext? {
        return EditorContextCapture.captureCurrentEditorContext(project)
    }
}

/**
 * Represents the current editor context (file and selection).
 */
data class EditorContext(
    val filePath: String,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val selectedText: String? = null,
)

/**
 * Utility for capturing editor context.
 */
object EditorContextCapture {
    private val log = logger<EditorContextCapture>()

    /**
     * Capture the current editor's file path and selection range.
     */
    fun captureCurrentEditorContext(project: Project): EditorContext? {
        return try {
            val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            val selectedEditor = fileEditorManager.selectedTextEditor

            if (selectedEditor != null) {
                val virtualFile = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                    .getFile(selectedEditor.document)
                val filePath = virtualFile?.path ?: return null

                val selectionModel = selectedEditor.selectionModel
                val startPosition = selectedEditor.offsetToLogicalPosition(selectionModel.selectionStart)
                val endPosition = selectedEditor.offsetToLogicalPosition(selectionModel.selectionEnd)

                // Only include line numbers if there's an actual selection
                val startLine = if (startPosition.line != endPosition.line ||
                                    startPosition.column != endPosition.column) {
                    startPosition.line
                } else {
                    null
                }
                val endLine = if (startLine != null) endPosition.line else null

                EditorContext(
                    filePath = filePath,
                    startLine = startLine,
                    endLine = endLine,
                    selectedText = selectionModel.selectedText,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            log.debug("Error capturing editor context: ${e.message}")
            null
        }
    }
}
