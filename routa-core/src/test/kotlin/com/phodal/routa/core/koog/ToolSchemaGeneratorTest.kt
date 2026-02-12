package com.phodal.routa.core.koog

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/**
 * Tests for [ToolSchemaGenerator] - verifies that tool schemas are correctly
 * generated from Koog SimpleTool instances.
 */
class ToolSchemaGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `generateToolsSchema should include all tools`() {
        val cwd = tempFolder.root.absolutePath
        val tools = listOf(
            ReadFileTool(cwd),
            ListFilesTool(cwd),
        )

        val schema = ToolSchemaGenerator.generateToolsSchema(tools)

        // Should contain both tool names
        assertTrue("Schema should contain read_file tool", schema.contains("### read_file"))
        assertTrue("Schema should contain list_files tool", schema.contains("### list_files"))
    }

    @Test
    fun `generateToolsSchema should include parameter descriptions`() {
        val cwd = tempFolder.root.absolutePath
        val tools = listOf(ReadFileTool(cwd))

        val schema = ToolSchemaGenerator.generateToolsSchema(tools)

        // Should contain the path parameter with its LLMDescription
        assertTrue("Schema should contain path parameter", schema.contains("\"path\""))
        assertTrue("Schema should specify string type", schema.contains("\"type\": \"string\""))
        assertTrue("Schema should have required section", schema.contains("\"required\""))
    }

    @Test
    fun `generateToolsSchema should include example tool calls`() {
        val cwd = tempFolder.root.absolutePath
        val tools = listOf(ReadFileTool(cwd))

        val schema = ToolSchemaGenerator.generateToolsSchema(tools)

        // Should contain example XML format
        assertTrue("Schema should contain tool_call example", schema.contains("<tool_call>"))
        assertTrue("Schema should contain closing tool_call tag", schema.contains("</tool_call>"))
        assertTrue("Schema should contain tool name in example", schema.contains("\"name\": \"read_file\""))
    }

    @Test
    fun `generateToolSchema should handle optional parameters`() {
        val cwd = tempFolder.root.absolutePath
        val tool = ListFilesTool(cwd)

        val schema = ToolSchemaGenerator.generateToolSchema(tool.descriptor)

        // list_files has optional path parameter (defaults to ".")
        assertTrue("Schema should contain path parameter", schema.contains("\"path\""))
        // The path parameter should be in optionalParameters, so required array may be empty
        assertTrue("Schema should have required section", schema.contains("\"required\":"))
    }

    @Test
    fun `generated schema should be valid for LLM consumption`() {
        val cwd = tempFolder.root.absolutePath
        val tools = listOf(
            ReadFileTool(cwd),
            ListFilesTool(cwd),
        )

        val schema = ToolSchemaGenerator.generateToolsSchema(tools)

        // Print for manual inspection during development
        println("=== Generated Tool Schema ===")
        println(schema)
        println("=== End Schema ===")

        // Basic structure checks
        assertTrue("Should have header", schema.contains("## Available Tools"))
        assertTrue("Should have parameters section", schema.contains("**Parameters:**"))
        assertTrue("Should have example section", schema.contains("**Example:**"))
    }
}

