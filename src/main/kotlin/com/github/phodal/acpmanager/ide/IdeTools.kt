package com.github.phodal.acpmanager.ide

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.awt.FlowLayout
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

private val log = logger<IdeTools>()

/**
 * IDE tools implementation — provides IDEA editor/file/diagnostic capabilities
 * that agents can invoke through tool calls.
 *
 * Mirrors Claude Code's EditorTools, FileTools, DiffTools, DiagnosticTools.
 */
class IdeTools(private val project: Project) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // ===== File Tools =====

    /**
     * Open a file in the editor.
     * Mirrors Claude Code's FileTools.openFile.
     */
    fun openFile(filePath: String, makeFrontmost: Boolean = true): ToolCallResult {
        val projectDir = getProjectDir() ?: return ToolCallResult.error("Project directory not found")
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        ApplicationManager.getApplication().invokeAndWait {
            val file = findVirtualFile(filePath, projectDir) ?: return@invokeAndWait
            if (file.exists()) {
                file.refresh(false, false)
                FileEditorManager.getInstance(project).openFile(file, makeFrontmost)
            }
        }
        return ToolCallResult.ok("OK")
    }

    /**
     * Open multiple files in the editor.
     * Mirrors Claude Code's FileTools.open_files.
     * Returns JSON with list of successfully opened files.
     */
    fun openFiles(filePaths: List<String>): ToolCallResult {
        val projectDir = getProjectDir() ?: return ToolCallResult.error("Project directory not found")
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        val openedFiles = mutableListOf<String>()
        ApplicationManager.getApplication().invokeAndWait {
            for (filePath in filePaths) {
                val file = findVirtualFile(filePath, projectDir)
                if (file != null && file.exists()) {
                    FileEditorManager.getInstance(project).openFile(file, true)
                    openedFiles.add(filePath)
                }
            }
        }
        val result = OpenedFilesResults(openedFiles)
        val jsonResult = json.encodeToString(OpenedFilesResults.serializer(), result)
        return ToolCallResult.ok(jsonResult)
    }

    /**
     * Close a tab by file path or tab name.
     * Mirrors Claude Code's FileTools.close_tab.
     * Supports both regular file tabs and diff editor tabs.
     */
    fun closeTab(tabName: String): ToolCallResult {
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        var found = false
        ApplicationManager.getApplication().invokeAndWait {
            val fileEditorManager = FileEditorManager.getInstance(project)

            // First, try to close diff editors by tab name
            closeDiffEditorsByTabName(tabName, fileEditorManager)

            // Then, try to close regular file tabs
            val editors = fileEditorManager.allEditors
            val matchingEditor = editors.firstOrNull { editor ->
                editor.file?.path == tabName ||
                        editor.file?.name == tabName ||
                        editor.file?.path?.endsWith(tabName) == true
            }
            if (matchingEditor != null) {
                fileEditorManager.closeFile(matchingEditor.file!!)
                found = true
            }

            // Also dispose windows with matching title (for diff viewer windows)
            disposeWindowsByTabName(tabName)
        }

        return if (found) ToolCallResult.ok("closed") else ToolCallResult.ok("not_found")
    }

    /**
     * Get all currently open file paths.
     * Mirrors Claude Code's FileTools.get_all_opened_file_paths.
     */
    fun getOpenFiles(): ToolCallResult {
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        var filePaths = emptyList<String>()
        ApplicationManager.getApplication().invokeAndWait {
            filePaths = FileEditorManager.getInstance(project)
                .openFiles
                .mapNotNull { it.path }
        }
        return ToolCallResult.ok(filePaths.joinToString("\n"))
    }

    // ===== Editor Tools =====

    /**
     * Reformat a file using IDE's code formatter.
     * Mirrors Claude Code's EditorTools.reformat_file.
     */
    fun reformatFile(filePath: String): ToolCallResult {
        val projectDir = getProjectDir() ?: return ToolCallResult.error("Project directory not found")
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        try {
            val psiFile = ApplicationManager.getApplication().runReadAction<PsiFile?> {
                val file = findVirtualFile(filePath, projectDir)
                if (file == null || !file.exists()) return@runReadAction null
                PsiManager.getInstance(project).findFile(file)
            }

            if (psiFile == null) return ToolCallResult.error("File not found: $filePath")

            val reformatCompleted = CountDownLatch(1)
            val codeProcessor = ReformatCodeProcessor(psiFile, false)
            codeProcessor.setPostRunnable { reformatCompleted.countDown() }
            ApplicationManager.getApplication().invokeLater { codeProcessor.run() }
            reformatCompleted.await()

            return ToolCallResult.ok("OK")
        } catch (e: Exception) {
            log.warn("Failed to reformat file: $filePath", e)
            return ToolCallResult.error("Error reformatting file: ${e.message}")
        }
    }

    // ===== Diff Tools =====

    /**
     * Open a diff view with original file vs proposed new content.
     * Mirrors Claude Code's DiffTools.openDiff with Accept/Reject UI.
     *
     * Features:
     * - Custom bottom panel with Accept/Reject buttons
     * - Auto-reject when diff is closed without action
     * - Focus management (Apply button is default)
     * - Keyboard shortcuts (Enter to accept, Esc to reject)
     *
     * Returns:
     * - "FILE_SAVED" + new content if accepted
     * - "DIFF_REJECTED" if rejected
     */
    suspend fun openDiff(
        oldFilePath: String,
        newFileContents: String,
        tabName: String = "Diff",
    ): ToolCallResult {
        val projectDir = getProjectDir() ?: return ToolCallResult.error("Project directory not found")
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        val result = CompletableDeferred<ToolCallResult>()

        ApplicationManager.getApplication().invokeAndWait {
            try {
                val contentFactory = DiffContentFactory.getInstance()
                val originalFile = findVirtualFile(oldFilePath, projectDir)
                val originalFileName = oldFilePath.substringAfterLast('/')

                // Determine file type
                val fileType = originalFile?.fileType
                    ?: FileTypeManager.getInstance().getFileTypeByFileName(originalFileName)

                // Create original content
                val originalContent: DocumentContent = if (originalFile != null) {
                    contentFactory.createDocument(project, originalFile)
                        ?: contentFactory.create(project, "", fileType)
                } else {
                    contentFactory.create(project, "", fileType)
                } as DocumentContent

                // Create proposed content
                val proposedFile = LightVirtualFile(originalFileName, fileType, newFileContents)
                val proposedContent: DiffContent = contentFactory.createDocument(project, proposedFile)
                    ?: contentFactory.create(project, proposedFile)

                // Build diff request
                val originalTitlePrefix = if (originalFile != null) "Original: " else "New: "
                val diffRequest = SimpleDiffRequest(
                    tabName,
                    originalContent,
                    proposedContent,
                    "$originalTitlePrefix$oldFilePath",
                    "Proposed"
                )

                // Mark original as read-only, proposed as editable
                diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, booleanArrayOf(true, false))
                diffRequest.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, Side.RIGHT)

                var actionApplied = false

                // Accept action
                val acceptAction = object : AnAction("Accept", "Accept proposed changes", AllIcons.Actions.Checked) {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (actionApplied) return
                        actionApplied = true
                        val updatedText = (proposedContent as? DocumentContent)?.document?.text
                            ?: proposedFile.content.toString()
                        val normalized = updatedText.replace("\r\n", "\n")
                        result.complete(ToolCallResult.ok("FILE_SAVED", normalized))
                    }

                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                }

                // Reject action
                val rejectAction = object : AnAction("Reject", "Reject proposed changes", AllIcons.Actions.Cancel) {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (actionApplied) return
                        actionApplied = true
                        result.complete(ToolCallResult.ok("DIFF_REJECTED"))
                    }

                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                }

                // Add context actions
                val actions = mutableListOf<AnAction>(rejectAction, acceptAction)
                diffRequest.putUserData(DiffUserDataKeysEx.CONTEXT_ACTIONS, actions)

                // Create bottom panel with JButton components
                val rejectButton = JButton("Reject").apply {
                    icon = AllIcons.Actions.Cancel
                    maximumSize = preferredSize
                    addActionListener {
                        if (!actionApplied) {
                            actionApplied = true
                            result.complete(ToolCallResult.ok("DIFF_REJECTED"))
                        }
                    }
                }

                val acceptButton = JButton("Accept").apply {
                    icon = AllIcons.Actions.Checked
                    maximumSize = preferredSize
                    addActionListener {
                        if (!actionApplied) {
                            actionApplied = true
                            val updatedText = (proposedContent as? DocumentContent)?.document?.text
                                ?: proposedFile.content.toString()
                            val normalized = updatedText.replace("\r\n", "\n")
                            result.complete(ToolCallResult.ok("FILE_SAVED", normalized))
                        }
                    }
                }

                // Button panel (horizontal layout)
                val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 4, 0)).apply {
                    add(rejectButton)
                    add(acceptButton)
                }

                // Bottom panel (vertical layout with padding)
                val bottomPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
                    add(buttonPanel)
                }

                diffRequest.putUserData(DiffUserDataKeysEx.BOTTOM_PANEL, bottomPanel)

                // Show diff
                val chain = SimpleDiffRequestChain(diffRequest)
                DiffManagerEx.getInstance().showDiffBuiltin(project, chain, DiffDialogHints.DEFAULT)

                // Set up auto-reject on close listener
                val connection = project.messageBus.connect()
                connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                        // Check if this is the diff view being closed
                        // If action hasn't been applied yet, auto-reject
                        if (!actionApplied && (file.name.contains("Diff") || file.path.contains(oldFilePath))) {
                            actionApplied = true
                            result.complete(ToolCallResult.ok("DIFF_REJECTED"))
                            connection.disconnect()
                        }
                    }
                })

            } catch (e: Exception) {
                log.warn("Error opening diff", e)
                result.complete(ToolCallResult.error("Error opening diff: ${e.message}"))
            }
        }

        // Wait for user action with a generous timeout
        return withTimeoutOrNull(600.seconds) { result.await() }
            ?: ToolCallResult.error("Diff view timed out")
    }

    // ===== Diagnostic Tools =====

    /**
     * Get diagnostics (errors/warnings) for a file.
     * Mirrors Claude Code's DiagnosticTools.getDiagnostics.
     *
     * @param uri File URI or path. If null, uses the currently active editor.
     */
    suspend fun getDiagnostics(uri: String?): ToolCallResult {
        val projectDir = getProjectDir() ?: return ToolCallResult.error("Project directory not found")
        if (!project.isOpen) return ToolCallResult.error("Project is closed")

        val result = CompletableDeferred<ToolCallResult>()

        ApplicationManager.getApplication().invokeAndWait {
            try {
                // Resolve target file
                val file: VirtualFile? = if (uri != null) {
                    val normalizedPath = uri
                        .removePrefix("file://")
                        .replace("_claude_fs_right:", "")
                    findVirtualFile(normalizedPath, projectDir)
                } else {
                    FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile
                }

                val psiFile = file?.let { PsiManager.getInstance(project).findFile(it) }
                if (psiFile == null) {
                    result.complete(ToolCallResult.error("File not found"))
                    return@invokeAndWait
                }

                file.refresh(false, false)

                val daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project)
                val connection = project.messageBus.connect()

                connection.subscribe(
                    DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
                    object : DaemonCodeAnalyzer.DaemonListener {
                        override fun daemonFinished() {
                            if (!daemonCodeAnalyzer.isErrorAnalyzingFinished(psiFile)) return

                            connection.disconnect()

                            val document = psiFile.fileDocument
                            val diagnostics = mutableListOf<DiagnosticItem>()

                            DaemonCodeAnalyzerEx.processHighlights(
                                document, project, HighlightSeverity.WEAK_WARNING,
                                0, document.textLength
                            ) { info: HighlightInfo ->
                                val description = info.description ?: return@processHighlights true

                                val lineStart = document.getLineNumber(info.startOffset)
                                val columnStart = info.startOffset - document.getLineStartOffset(lineStart)
                                val lineEnd = document.getLineNumber(info.endOffset)
                                val columnEnd = info.endOffset - document.getLineStartOffset(lineEnd)

                                diagnostics.add(
                                    DiagnosticItem(
                                        message = description,
                                        severity = DiagnosticSeverity.from(info.severity.name).name,
                                        startLine = lineStart,
                                        startColumn = columnStart,
                                        endLine = lineEnd,
                                        endColumn = columnEnd
                                    )
                                )
                                true
                            }

                            val effectiveUri = uri ?: "file://${file.path}"
                            val fileDiagnostics = FileDiagnostics(effectiveUri, diagnostics)
                            val jsonResult = json.encodeToString(FileDiagnostics.serializer(), fileDiagnostics)
                            result.complete(ToolCallResult.ok(jsonResult))
                        }
                    }
                )

                daemonCodeAnalyzer.restart(psiFile)
            } catch (e: Exception) {
                log.warn("Error getting diagnostics", e)
                result.complete(ToolCallResult.error("Error getting diagnostics: ${e.message}"))
            }
        }

        return withTimeoutOrNull(5.seconds) { result.await() }
            ?: ToolCallResult.error("Timeout getting diagnostics")
    }

    // ===== Helpers =====

    /**
     * Close diff editor tabs by matching the tab name against diff request names.
     * Handles ChainDiffVirtualFile editors used by the diff viewer.
     */
    private fun closeDiffEditorsByTabName(tabName: String, fileEditorManager: FileEditorManager) {
        try {
            val editors = fileEditorManager.allEditors.toList()
            for (editor in editors) {
                val file = editor.file
                // Check if this is a ChainDiffVirtualFile (diff editor)
                if (file != null && file.javaClass.simpleName == "ChainDiffVirtualFile") {
                    try {
                        // Try to get the chain and first request name
                        val chainMethod = file.javaClass.getMethod("getChain")
                        val chain = chainMethod.invoke(file)
                        if (chain != null) {
                            val getRequestsMethod = chain.javaClass.getMethod("getRequests")
                            @Suppress("UNCHECKED_CAST")
                            val requests = getRequestsMethod.invoke(chain) as? List<Any>
                            if (requests != null && requests.isNotEmpty()) {
                                val firstRequest = requests[0]
                                val getNameMethod = firstRequest.javaClass.getMethod("getName")
                                val diffEditorName = getNameMethod.invoke(firstRequest) as? String
                                if (diffEditorName == tabName) {
                                    fileEditorManager.closeFile(file)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("Error closing diff editor: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Error in closeDiffEditorsByTabName: ${e.message}")
        }
    }

    /**
     * Dispose windows (JFrames) with matching title.
     * Used to close diff viewer windows opened by the IDE.
     */
    private fun disposeWindowsByTabName(tabName: String) {
        try {
            val windows = java.awt.Window.getWindows()
            for (window in windows) {
                if (window is javax.swing.JFrame && window.title == tabName) {
                    window.dispose()
                }
            }
        } catch (e: Exception) {
            log.debug("Error disposing windows: ${e.message}")
        }
    }

    private fun getProjectDir(): Path? {
        val dir = project.guessProjectDir() ?: return null
        return dir.toNioPathOrNull()
    }

    private fun findVirtualFile(path: String, projectDir: Path): VirtualFile? {
        val fs = LocalFileSystem.getInstance()
        return ApplicationManager.getApplication().runReadAction<VirtualFile?> {
            fs.findFileByPath(path) ?: fs.refreshAndFindFileByNioFile(projectDir.resolve(path))
        }
    }
}
