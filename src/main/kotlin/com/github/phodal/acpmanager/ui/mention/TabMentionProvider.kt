package com.github.phodal.acpmanager.ui.mention

import com.github.phodal.acpmanager.ui.fuzzy.FuzzyMatcher
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
        val mentions = mutableListOf<Pair<MentionItem, Int>>()  // Item + score
        val fileEditorManager = FileEditorManager.getInstance(project)

        // Get all open files
        val openFiles = fileEditorManager.openFiles

        for (file in openFiles) {
            val matchResult = FuzzyMatcher.match(file.name, query)
            if (matchResult.matched) {
                val relativePath = getRelativePath(file.path)
                val icon = AllIcons.FileTypes.Text

                val item = MentionItem(
                    type = MentionType.TAB,
                    displayText = file.name,
                    insertText = file.path,
                    icon = icon,
                    tailText = relativePath,
                    metadata = mapOf("path" to file.path)
                )
                mentions.add(item to matchResult.score)
            }
        }

        // Sort by score (descending), then by name length
        return mentions.sortedWith(compareBy({ -it.second }, { it.first.displayText.length }))
            .map { it.first }
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

}

