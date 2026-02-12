package com.phodal.routa.core.koog

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType

/**
 * Generates text-based tool schema from Koog [SimpleTool] instances.
 *
 * This is used to embed tool descriptions in system prompts for text-based
 * tool calling (using `<tool_call>` XML blocks) instead of native function calling.
 *
 * The generated schema follows JSON Schema format that LLMs understand well.
 */
object ToolSchemaGenerator {

    /**
     * Generate a complete tool schema section for multiple tools.
     *
     * @param tools List of SimpleTool instances to generate schema for
     * @return Formatted string containing all tool schemas
     */
    fun generateToolsSchema(tools: List<SimpleTool<*>>): String {
        return buildString {
            appendLine("## Available Tools")
            appendLine()
            appendLine("You have access to the following tools. Use them by wrapping a JSON object in `<tool_call>` tags.")
            appendLine()
            tools.forEach { tool ->
                append(generateToolSchema(tool.descriptor))
                appendLine()
            }
        }
    }

    /**
     * Generate schema for a single tool from its [ToolDescriptor].
     */
    fun generateToolSchema(descriptor: ToolDescriptor): String {
        return buildString {
            appendLine("### ${descriptor.name}")
            appendLine()
            appendLine(descriptor.description)
            appendLine()
            appendLine("**Parameters:**")
            appendLine("```json")
            appendLine("{")
            appendLine("  \"type\": \"object\",")
            appendLine("  \"properties\": {")
            
            val allParams = descriptor.requiredParameters + descriptor.optionalParameters
            allParams.forEachIndexed { index, param ->
                val isLast = index == allParams.size - 1
                append(generateParameterSchema(param, indent = 4, isLast = isLast))
            }
            
            appendLine("  },")
            
            if (descriptor.requiredParameters.isNotEmpty()) {
                val requiredNames = descriptor.requiredParameters.map { "\"${it.name}\"" }
                appendLine("  \"required\": [${requiredNames.joinToString(", ")}]")
            } else {
                appendLine("  \"required\": []")
            }
            
            appendLine("}")
            appendLine("```")
            appendLine()
            appendLine("**Example:**")
            appendLine("```xml")
            appendLine("<tool_call>")
            appendLine(generateExampleCall(descriptor))
            appendLine("</tool_call>")
            appendLine("```")
        }
    }

    private fun generateParameterSchema(param: ToolParameterDescriptor, indent: Int, isLast: Boolean): String {
        val spaces = " ".repeat(indent)
        val comma = if (isLast) "" else ","
        
        return buildString {
            appendLine("$spaces\"${param.name}\": {")
            appendLine("$spaces  \"type\": \"${mapTypeToJsonSchema(param.type)}\",")
            appendLine("$spaces  \"description\": \"${param.description}\"")
            appendLine("$spaces}$comma")
        }
    }

    private fun mapTypeToJsonSchema(type: ToolParameterType): String {
        return when (type) {
            is ToolParameterType.String -> "string"
            is ToolParameterType.Integer -> "integer"
            is ToolParameterType.Float -> "number"
            is ToolParameterType.Boolean -> "boolean"
            is ToolParameterType.List -> "array"
            is ToolParameterType.Enum -> "string"
            is ToolParameterType.Object -> "object"
            is ToolParameterType.Null -> "null"
            is ToolParameterType.AnyOf -> "object"
        }
    }

    private fun generateExampleCall(descriptor: ToolDescriptor): String {
        val exampleArgs = buildString {
            append("{")
            val params = descriptor.requiredParameters
            params.forEachIndexed { index, param ->
                if (index > 0) append(", ")
                append("\"${param.name}\": ${generateExampleValue(param)}")
            }
            append("}")
        }
        
        return """{"name": "${descriptor.name}", "arguments": $exampleArgs}"""
    }

    private fun generateExampleValue(param: ToolParameterDescriptor): String {
        return when (param.type) {
            is ToolParameterType.String -> "\"example_value\""
            is ToolParameterType.Integer -> "42"
            is ToolParameterType.Float -> "3.14"
            is ToolParameterType.Boolean -> "true"
            is ToolParameterType.List -> "[]"
            is ToolParameterType.Enum -> {
                val entries = (param.type as ToolParameterType.Enum).entries
                if (entries.isNotEmpty()) "\"${entries[0]}\"" else "\"value\""
            }
            is ToolParameterType.Object -> "{}"
            is ToolParameterType.Null -> "null"
            is ToolParameterType.AnyOf -> "{}"
        }
    }
}

