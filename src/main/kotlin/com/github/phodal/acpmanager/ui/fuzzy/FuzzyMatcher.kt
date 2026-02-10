package com.github.phodal.acpmanager.ui.fuzzy

/**
 * Fuzzy matching algorithm for mention and command search.
 *
 * Features:
 * - Case-insensitive matching
 * - Matches characters in order (not necessarily consecutive)
 * - Scores matches based on quality (consecutive matches score higher)
 * - Supports both substring and fuzzy matching
 *
 * Examples:
 * - "MyC" matches "MyClass" (score: high - consecutive match)
 * - "test" matches "test_file.kt" (score: high - prefix match)
 * - "MC" matches "MyClass" (score: medium - non-consecutive match)
 * - "xyz" does not match "MyClass" (no match)
 */
object FuzzyMatcher {
    /**
     * Represents a fuzzy match result.
     *
     * @param matched Whether the query matches the text
     * @param score Match quality score (higher is better). Range: 0-1000
     *   - 1000: Exact match (case-insensitive)
     *   - 900-999: Prefix match (query is at start of text)
     *   - 800-899: Consecutive substring match
     *   - 500-799: Fuzzy match with good consecutive sequences
     *   - 1-499: Fuzzy match with scattered characters
     *   - 0: No match
     */
    data class MatchResult(val matched: Boolean, val score: Int)

    /**
     * Check if query matches text and return match quality score.
     *
     * @param text The text to match against (e.g., filename, symbol name)
     * @param query The query string (e.g., user input)
     * @return MatchResult with matched flag and score
     */
    fun match(text: String, query: String): MatchResult {
        if (query.isEmpty()) {
            return MatchResult(matched = true, score = 0)
        }

        val textLower = text.lowercase()
        val queryLower = query.lowercase()

        // Check for exact match
        if (textLower == queryLower) {
            return MatchResult(matched = true, score = 1000)
        }

        // Check for prefix match
        if (textLower.startsWith(queryLower)) {
            val score = 900 + (100 - minOf(queryLower.length, 100))
            return MatchResult(matched = true, score = score)
        }

        // Check for consecutive substring match
        if (textLower.contains(queryLower)) {
            val score = 800 + (100 - minOf(queryLower.length, 100))
            return MatchResult(matched = true, score = score)
        }

        // Try fuzzy matching
        val fuzzyResult = fuzzyMatch(textLower, queryLower)
        return if (fuzzyResult.matched) {
            MatchResult(matched = true, score = fuzzyResult.score)
        } else {
            MatchResult(matched = false, score = 0)
        }
    }

    /**
     * Perform fuzzy matching and calculate score based on match quality.
     * Scores higher for consecutive character matches.
     */
    private fun fuzzyMatch(text: String, query: String): MatchResult {
        var textIdx = 0
        var queryIdx = 0
        var consecutiveMatches = 0
        var maxConsecutive = 0
        var totalMatches = 0

        while (textIdx < text.length && queryIdx < query.length) {
            if (text[textIdx] == query[queryIdx]) {
                consecutiveMatches++
                totalMatches++
                queryIdx++
                maxConsecutive = maxOf(maxConsecutive, consecutiveMatches)
            } else {
                consecutiveMatches = 0
            }
            textIdx++
        }

        // All query characters must be matched
        if (queryIdx < query.length) {
            return MatchResult(matched = false, score = 0)
        }

        // Calculate score based on:
        // 1. How many consecutive matches we had (higher is better)
        // 2. How early the matches started (earlier is better)
        // 3. How many total characters we had to scan (fewer is better)
        val consecutiveBonus = maxConsecutive * 50
        val lengthPenalty = (textIdx - query.length) * 2
        val score = maxOf(1, 500 + consecutiveBonus - lengthPenalty)

        return MatchResult(matched = true, score = minOf(score, 799))
    }

    /**
     * Get all matching positions in text for highlighting.
     * Returns list of (startIndex, endIndex) pairs for matched characters.
     *
     * @param text The text to search in
     * @param query The query string
     * @return List of (startIndex, endIndex) pairs for matched characters
     */
    fun getMatchPositions(text: String, query: String): List<Pair<Int, Int>> {
        if (query.isEmpty()) return emptyList()

        val textLower = text.lowercase()
        val queryLower = query.lowercase()
        val positions = mutableListOf<Pair<Int, Int>>()

        // For exact/prefix/substring matches, find the match position
        val exactMatchStart = textLower.indexOf(queryLower)
        if (exactMatchStart >= 0) {
            positions.add(exactMatchStart to exactMatchStart + queryLower.length)
            return positions
        }

        // For fuzzy matches, find each character position
        var textIdx = 0
        var queryIdx = 0
        var matchStart = -1

        while (textIdx < text.length && queryIdx < query.length) {
            if (text[textIdx].lowercaseChar() == query[queryIdx].lowercaseChar()) {
                if (matchStart == -1) {
                    matchStart = textIdx
                }
                queryIdx++
            } else {
                if (matchStart != -1) {
                    positions.add(matchStart to textIdx)
                    matchStart = -1
                }
            }
            textIdx++
        }

        if (matchStart != -1) {
            positions.add(matchStart to textIdx)
        }

        return positions
    }
}

