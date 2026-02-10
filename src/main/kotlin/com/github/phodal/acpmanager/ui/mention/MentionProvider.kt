package com.github.phodal.acpmanager.ui.mention

import javax.swing.Icon

/**
 * Represents a single mention item (file, symbol, tab, etc.)
 */
data class MentionItem(
    val type: MentionType,
    val displayText: String,
    val insertText: String,
    val icon: Icon? = null,
    val tailText: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Types of mentions supported
 */
enum class MentionType {
    FILE, SYMBOL, TAB, AGENT
}

/**
 * Interface for mention providers.
 * Implementations provide suggestions for different mention types.
 */
interface MentionProvider {
    /**
     * Get mention items matching the given query.
     *
     * @param query The partial text typed by the user (without the @ prefix)
     * @return List of matching mention items, sorted by relevance
     */
    fun getMentions(query: String): List<MentionItem>

    /**
     * Get the mention type this provider handles.
     */
    fun getMentionType(): MentionType
}

