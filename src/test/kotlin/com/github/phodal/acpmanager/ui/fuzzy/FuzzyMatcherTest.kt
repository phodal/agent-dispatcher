package com.github.phodal.acpmanager.ui.fuzzy

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class FuzzyMatcherTest : BasePlatformTestCase() {

    @Test
    fun testExactMatch() {
        val result = FuzzyMatcher.match("MyClass", "MyClass")
        assertTrue(result.matched)
        assertEquals(1000, result.score)
    }

    @Test
    fun testExactMatchCaseInsensitive() {
        val result = FuzzyMatcher.match("MyClass", "myclass")
        assertTrue(result.matched)
        assertEquals(1000, result.score)
    }

    @Test
    fun testPrefixMatch() {
        val result = FuzzyMatcher.match("MyClass", "My")
        assertTrue(result.matched)
        assertTrue(result.score in 900..999)
    }

    @Test
    fun testPrefixMatchCaseInsensitive() {
        val result = FuzzyMatcher.match("MyClass", "my")
        assertTrue(result.matched)
        assertTrue(result.score in 900..999)
    }

    @Test
    fun testSubstringMatch() {
        val result = FuzzyMatcher.match("MyClass", "Class")
        assertTrue(result.matched)
        assertTrue(result.score in 800..899)
    }

    @Test
    fun testSubstringMatchCaseInsensitive() {
        val result = FuzzyMatcher.match("MyClass", "class")
        assertTrue(result.matched)
        assertTrue(result.score in 800..899)
    }

    @Test
    fun testFuzzyMatch() {
        val result = FuzzyMatcher.match("MyClass", "MC")
        assertTrue(result.matched)
        assertTrue(result.score in 1..799)
    }

    @Test
    fun testFuzzyMatchCaseInsensitive() {
        val result = FuzzyMatcher.match("MyClass", "mc")
        assertTrue(result.matched)
        assertTrue(result.score in 1..799)
    }

    @Test
    fun testNoMatch() {
        val result = FuzzyMatcher.match("MyClass", "xyz")
        assertFalse(result.matched)
        assertEquals(0, result.score)
    }

    @Test
    fun testEmptyQuery() {
        val result = FuzzyMatcher.match("MyClass", "")
        assertTrue(result.matched)
        assertEquals(0, result.score)
    }

    @Test
    fun testPartialFilename() {
        val result = FuzzyMatcher.match("test_file.kt", "test")
        assertTrue(result.matched)
        assertTrue(result.score in 900..999)
    }

    @Test
    fun testPartialFilenameMiddle() {
        val result = FuzzyMatcher.match("test_file.kt", "file")
        assertTrue(result.matched)
        assertTrue(result.score in 800..899)
    }

    @Test
    fun testFuzzyFilename() {
        val result = FuzzyMatcher.match("test_file.kt", "tft")
        assertTrue(result.matched)
        assertTrue(result.score in 1..799)
    }

    @Test
    fun testScoringConsecutiveMatches() {
        // "test" should score higher than "tst" in "test_file"
        val result1 = FuzzyMatcher.match("test_file", "test")
        val result2 = FuzzyMatcher.match("test_file", "tst")
        assertTrue(result1.score > result2.score)
    }

    @Test
    fun testGetMatchPositions() {
        val positions = FuzzyMatcher.getMatchPositions("MyClass", "MyClass")
        assertEquals(1, positions.size)
        assertEquals(0 to 7, positions[0])
    }

    @Test
    fun testGetMatchPositionsFuzzy() {
        val positions = FuzzyMatcher.getMatchPositions("MyClass", "MC")
        assertTrue(positions.isNotEmpty())
    }

    @Test
    fun testGetMatchPositionsEmpty() {
        val positions = FuzzyMatcher.getMatchPositions("MyClass", "")
        assertEquals(0, positions.size)
    }

    @Test
    fun testGetMatchPositionsNoMatch() {
        val positions = FuzzyMatcher.getMatchPositions("MyClass", "xyz")
        assertEquals(0, positions.size)
    }
}

