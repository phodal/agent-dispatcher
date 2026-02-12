package com.phodal.routa.core.koog

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Koog-compatible [SimpleTool] wrappers for workspace file operations.
 *
 * Inspired by Intent's `workspace-file-tools.ts`:
 * - [ReadFileTool] — read project files (NOT notes or spec)
 * - [WriteFileTool] — write/create project files
 * - [ListFilesTool] — list directory contents
 *
 * All file paths are resolved relative to the workspace root (`cwd`).
 * Access is restricted to paths within the workspace to prevent escape.
 *
 * Usage:
 * ```kotlin
 * val toolRegistry = WorkspaceToolRegistry.create(agentTools, workspaceId, cwd = "/path/to/project")
 * ```
 */

// ── read_file ───────────────────────────────────────────────────────────

@Serializable
data class ReadFileArgs(
    @property:LLMDescription("Relative path to the file from workspace root, e.g. 'src/main.kt' or 'package.json'. REQUIRED.")
    val path: String,
)

class ReadFileTool(
    private val cwd: String,
) : SimpleTool<ReadFileArgs>(
    argsSerializer = ReadFileArgs.serializer(),
    name = "read_file",
    description = "Read the contents of a file in the workspace project directory. " +
        "Provide a path relative to the workspace root (e.g., 'src/App.tsx', 'package.json'). " +
        "Use this for reading actual project files, NOT for notes or spec.",
) {
    override suspend fun execute(args: ReadFileArgs): String {
        val filePath = args.path
        if (filePath.isBlank()) {
            return "Error: File path is required"
        }

        val resolved = resolveSafely(cwd, filePath)
            ?: return "Error: Access denied — path outside workspace"

        return try {
            val content = resolved.toFile().readText()
            content
        } catch (e: Exception) {
            "Error: Failed to read file '$filePath': ${e.message}"
        }
    }
}

// ── write_file ──────────────────────────────────────────────────────────

@Serializable
data class WriteFileArgs(
    @property:LLMDescription("Relative path to the file from workspace root, e.g. 'src/main.kt'. REQUIRED.")
    val path: String,
    @property:LLMDescription("The content to write to the file. REQUIRED.")
    val content: String,
)

class WriteFileTool(
    private val cwd: String,
) : SimpleTool<WriteFileArgs>(
    argsSerializer = WriteFileArgs.serializer(),
    name = "write_file",
    description = "Write contents to a file in the workspace project directory. " +
        "Provide a path relative to the workspace root and the content to write. " +
        "Creates parent directories automatically if they don't exist.",
) {
    override suspend fun execute(args: WriteFileArgs): String {
        val filePath = args.path
        val content = args.content

        if (filePath.isBlank()) {
            return "Error: File path is required"
        }

        val resolved = resolveSafely(cwd, filePath)
            ?: return "Error: Access denied — path outside workspace"

        return try {
            // Create parent directories if they don't exist
            resolved.parent?.let { Files.createDirectories(it) }
            resolved.toFile().writeText(content)
            "File written successfully: $filePath (${content.length} bytes)"
        } catch (e: Exception) {
            "Error: Failed to write file '$filePath': ${e.message}"
        }
    }
}

// ── list_files ──────────────────────────────────────────────────────────

@Serializable
data class ListFilesArgs(
    @property:LLMDescription("Relative path to the directory from workspace root. Defaults to '.' (root). Optional.")
    val path: String = ".",
)

class ListFilesTool(
    private val cwd: String,
) : SimpleTool<ListFilesArgs>(
    argsSerializer = ListFilesArgs.serializer(),
    name = "list_files",
    description = "List files and directories within the workspace project directory. " +
        "Provide a path relative to the workspace root (defaults to root). " +
        "Returns entries with name and type (file/directory).",
) {
    override suspend fun execute(args: ListFilesArgs): String {
        val dirPath = args.path.ifBlank { "." }

        val resolved = resolveSafely(cwd, dirPath)
            ?: return "Error: Access denied — path outside workspace"

        val dir = resolved.toFile()
        if (!dir.exists()) {
            return "Error: Directory not found: $dirPath"
        }
        if (!dir.isDirectory) {
            return "Error: Not a directory: $dirPath"
        }

        return try {
            val entries = dir.listFiles()
                ?.sortedBy { it.name }
                ?.map { entry ->
                    val type = if (entry.isDirectory) "directory" else "file"
                    """  {"name": "${entry.name}", "type": "$type"}"""
                }
                ?: emptyList()

            "[\n${entries.joinToString(",\n")}\n]"
        } catch (e: Exception) {
            "Error: Failed to list files in '$dirPath': ${e.message}"
        }
    }
}

// ── Path safety ─────────────────────────────────────────────────────────

/**
 * Resolve a relative path against the workspace root, ensuring it stays within bounds.
 *
 * Returns `null` if the resolved path escapes the workspace directory.
 */
private fun resolveSafely(cwd: String, relativePath: String): Path? {
    val base = Paths.get(cwd).toAbsolutePath().normalize()
    val resolved = base.resolve(relativePath).normalize()
    return if (resolved.startsWith(base)) resolved else null
}
