package com.github.phodal.acpmanager.ui.mention

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

private val log = logger<TabMentionProvider>()

/**
 * Provides mention suggestions for open editor tabs.
 *
 * Features:
 * - Suggests currently open files in editor tabs
 * - Shows file icon
 * - Shows relative path in tail text
 * - Supports fuzzy matching on file names
 */
class TabMentionProvider(private val project: Project) : MentionProvider {

    override fun getMentionType(): MentionType = MentionType.TAB

    override fun getMentions(query: String): List<MentionItem> {
        val mentions = mutableListOf<MentionItem>()
        val fileEditorManager = FileEditorManager.getInstance(project)
        
        // Get all open files
        val openFiles = fileEditorManager.openFiles
        
        for (file in openFiles) {
            if (matchesQuery(file.name, query)) {
                val relativePath = getRelativePath(file.path)
                val icon = AllIcons.FileTypes.Text
                
                mentions.add(
                    MentionItem(
                        type = MentionType.TAB,
                        displayText = file.name,
                        insertText = file.path,
                        icon = icon,
                        tailText = relativePath,
                        metadata = mapOf("path" to file.path)
                    )
                )
            }
        }
        
        // Sort by relevance: exact matches first, then by name length
        return mentions.sortedWith(compareBy(
            { !it.displayText.equals(query, ignoreCase = true) },
            { it.displayText.length }
        ))
    }

    private fun getRelativePath(filePath: String): String {
        return try {
            val basePath = project.basePath ?: return filePath
            if (filePath.startsWith(basePath)) {
                filePath.substring(basePath.length).removePrefix("/")
            } else {
                filePath
            }
        } catch (e: Exception) {
            filePath
        }
    }

    private fun matchesQuery(fileName: String, query: String): Boolean {
        if (query.isEmpty()) return true
        return fileName.contains(query, ignoreCase = true)
    }
}

