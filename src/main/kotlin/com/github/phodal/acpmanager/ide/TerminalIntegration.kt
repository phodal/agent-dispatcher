package com.github.phodal.acpmanager.ide

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.regex.Pattern

private val log = logger<TerminalIntegration>()

/**
 * Terminal integration for file path hyperlink detection.
 *
 * Detects file paths in terminal output and makes them clickable.
 * Supports formats:
 * - `/path/to/file`
 * - `file.kt:123` (with line number)
 * - `file.kt:123:45` (with line and column)
 * - `./relative/path`
 * - `../relative/path`
 */
class TerminalIntegration : ConsoleFilterProvider {

    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(FilePathFilter(project))
    }

    /**
     * Console filter that detects file paths and creates hyperlinks.
     */
    private class FilePathFilter(private val project: Project) : Filter {

        // Regex patterns for different path formats
        private val patterns = listOf(
            // Absolute paths: /path/to/file or /path/to/file:123 or /path/to/file:123:45
            Pattern.compile("""(/[^\s:]+?)(?::(\d+))?(?::(\d+))?(?=\s|$)"""),
            // Relative paths: ./file or ../file or file.ext
            Pattern.compile("""((?:\./|\.\./)[\w./\-]+|[\w\-]+\.\w+)(?::(\d+))?(?::(\d+))?(?=\s|$)"""),
        )

        override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
            val results = mutableListOf<Filter.ResultItem>()
            val projectDir = project.basePath ?: return null

            for (pattern in patterns) {
                val matcher = pattern.matcher(line)
                while (matcher.find()) {
                    val pathStr = matcher.group(1)
                    val lineNum = matcher.group(2)?.toIntOrNull()
                    val colNum = matcher.group(3)?.toIntOrNull()

                    // Resolve the file path
                    val file = resolveFile(pathStr, projectDir) ?: continue

                    // Create hyperlink info
                    val hyperlinkInfo = FilePathHyperlinkInfo(project, file, lineNum, colNum)

                    // Calculate offsets relative to entire console output
                    val startOffset = entireLength - line.length + matcher.start(1)
                    val endOffset = entireLength - line.length + matcher.end()

                    results.add(Filter.ResultItem(startOffset, endOffset, hyperlinkInfo))
                }
            }

            return if (results.isNotEmpty()) Filter.Result(results) else null
        }

        /**
         * Resolve a file path to a VirtualFile.
         * Handles both absolute and relative paths.
         */
        private fun resolveFile(pathStr: String, projectDir: String): VirtualFile? {
            return try {
                val file = File(pathStr)
                val resolvedPath = if (file.isAbsolute) {
                    file.absolutePath
                } else {
                    File(projectDir, pathStr).absolutePath
                }

                LocalFileSystem.getInstance().findFileByPath(resolvedPath)
            } catch (e: Exception) {
                log.debug("Failed to resolve file path: $pathStr", e)
                null
            }
        }
    }

    /**
     * Hyperlink info that opens a file in the editor.
     */
    private class FilePathHyperlinkInfo(
        private val project: Project,
        private val file: VirtualFile,
        private val lineNumber: Int?,
        private val columnNumber: Int?,
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            try {
                // Open the file
                val descriptor = if (lineNumber != null) {
                    // Line numbers are 1-based in the UI, but 0-based in OpenFileDescriptor
                    OpenFileDescriptor(project, file, lineNumber - 1, columnNumber ?: 0)
                } else {
                    OpenFileDescriptor(project, file)
                }

                descriptor.navigate(true)

                // Focus the editor
                val editor = FileEditorManager.getInstance(project).getSelectedTextEditor()
                editor?.contentComponent?.requestFocus()

                log.debug("Opened file: ${file.path} at line: $lineNumber, column: $columnNumber")
            } catch (e: Exception) {
                log.warn("Failed to navigate to file: ${file.path}", e)
            }
        }
    }
}

