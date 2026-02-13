package com.phodal.routa.core.report

import com.phodal.routa.core.model.CompletionReport
import com.phodal.routa.core.model.Task
import com.phodal.routa.core.model.VerificationVerdict

/**
 * Default report parser that uses text pattern matching to extract
 * structured information from LLM output.
 *
 * This is the fallback parser for when the LLM doesn't support
 * function calling (i.e., the agent didn't call `report_to_parent`
 * via tool calls, so we need to parse the text output).
 *
 * ## Parsing heuristics:
 * - **CRAFTER**: Extracts summary (first 3 lines), file paths (regex),
 *   and success/failure (absence of "FAILED"/"ERROR" keywords).
 * - **GATE**: Looks for "APPROVED" / "NOT APPROVED" keywords per task
 *   section, identified by task ID or title.
 */
class TextPatternReportParser : ReportParser {

    override fun parseCrafterReport(
        agentId: String,
        taskId: String,
        output: String,
    ): CompletionReport {
        return CompletionReport(
            agentId = agentId,
            taskId = taskId,
            summary = extractSummary(output),
            filesModified = extractFilesModified(output),
            success = !output.contains("FAILED", ignoreCase = true) &&
                !output.contains("ERROR", ignoreCase = true),
        )
    }

    override fun parseGateVerdicts(
        gateAgentId: String,
        output: String,
        tasks: List<Task>,
    ): Map<String, GateTaskVerdict> {
        val verdicts = mutableMapOf<String, GateTaskVerdict>()

        for (task in tasks) {
            val taskSection = findTaskSection(output, task)
            val approved = taskSection.contains("APPROVED", ignoreCase = true) &&
                !taskSection.contains("NOT APPROVED", ignoreCase = true) &&
                !taskSection.contains("NOT_APPROVED", ignoreCase = true)

            verdicts[task.id] = GateTaskVerdict(
                verdict = if (approved) VerificationVerdict.APPROVED else VerificationVerdict.NOT_APPROVED,
                summary = extractSummary(taskSection.ifEmpty { output }),
            )
        }

        return verdicts
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /**
     * Extract a summary from agent output (first 2-3 non-blank lines).
     */
    internal fun extractSummary(output: String): String {
        val lines = output.lines().filter { it.isNotBlank() }
        return lines.take(3).joinToString(" ").take(500)
    }

    /**
     * Try to extract file paths mentioned in the output.
     */
    internal fun extractFilesModified(output: String): List<String> {
        val fileRegex = Regex("""(?:src|lib|app|test)/[\w/.-]+\.\w+""")
        return fileRegex.findAll(output).map { it.value }.distinct().toList()
    }

    /**
     * Find the section of the GATE output relevant to a specific task.
     * Falls back to the full output if no task-specific section is found.
     */
    internal fun findTaskSection(output: String, task: Task): String {
        val lines = output.lines()
        val taskIdentifiers = listOfNotNull(task.id, task.title).filter { it.isNotBlank() }

        for (identifier in taskIdentifiers) {
            val startIdx = lines.indexOfFirst { it.contains(identifier, ignoreCase = true) }
            if (startIdx >= 0) {
                val sectionLines = mutableListOf<String>()
                for (i in startIdx until lines.size) {
                    val line = lines[i]
                    if (i > startIdx && taskIdentifiers.none { line.contains(it, ignoreCase = true) } &&
                        (line.startsWith("## ") || line.startsWith("---"))
                    ) {
                        break
                    }
                    sectionLines.add(line)
                }
                return sectionLines.joinToString("\n")
            }
        }

        return output
    }
}
