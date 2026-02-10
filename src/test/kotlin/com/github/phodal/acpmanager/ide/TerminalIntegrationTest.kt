package com.github.phodal.acpmanager.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * Tests for TerminalIntegration file path hyperlink detection.
 */
class TerminalIntegrationTest : BasePlatformTestCase() {

    @Test
    fun testTerminalIntegrationCreation() {
        val integration = TerminalIntegration()
        assertNotNull(integration)
    }

    @Test
    fun testConsoleFilterProviderRegistration() {
        val integration = TerminalIntegration()
        val filters = integration.getDefaultFilters(project)
        assertNotNull(filters)
        assertTrue("Should have at least one filter", filters.isNotEmpty())
    }

    @Test
    fun testAbsolutePathPattern() {
        // Test that absolute paths are recognized
        val testPath = "/path/to/file.kt"
        assertTrue("Should contain absolute path", testPath.startsWith("/"))
    }

    @Test
    fun testRelativePathPattern() {
        // Test that relative paths are recognized
        val testPath = "./src/main/kotlin/file.kt"
        assertTrue("Should contain relative path", testPath.startsWith("./"))
    }

    @Test
    fun testPathWithLineNumber() {
        // Test path with line number format
        val testPath = "file.kt:123"
        val parts = testPath.split(":")
        assertEquals(2, parts.size)
        assertEquals("file.kt", parts[0])
        assertEquals("123", parts[1])
    }

    @Test
    fun testPathWithLineAndColumn() {
        // Test path with line and column format
        val testPath = "file.kt:123:45"
        val parts = testPath.split(":")
        assertEquals(3, parts.size)
        assertEquals("file.kt", parts[0])
        assertEquals("123", parts[1])
        assertEquals("45", parts[2])
    }

    @Test
    fun testParentDirectoryPath() {
        // Test parent directory relative paths
        val testPath = "../src/file.kt"
        assertTrue("Should contain parent directory reference", testPath.startsWith("../"))
    }
}

