package com.phodal.routa.core.report

import com.phodal.routa.core.model.CompletionReport
import com.phodal.routa.core.model.Task
import com.phodal.routa.core.model.VerificationVerdict

/**
 * Strategy for parsing LLM text output into structured reports.
 *
 * Different LLMs produce output in different formats. This interface
 * decouples the orchestration logic from the output parsing logic,
 * allowing different parsers for different providers:
 *
 * - [TextPatternReportParser]: Regex-based parsing for plain-text LLM output.
 * - Custom implementations can handle JSON mode, structured output, etc.
 *
 * ## When parsing is NOT needed
 *
 * When the LLM supports function calling (e.g., Koog), the agent calls
 * `report_to_parent` directly via tool calls, updating the stores without
 * needing text parsing. In that case, the orchestration stages check
 * the stores first and only fall back to this parser if the agent
 * didn't call the tool.
 *
 * @see TextPatternReportParser for the default regex-based implementation.
 */
interface ReportParser {

    /**
     * Parse a CRAFTER's text output into a [CompletionReport].
     *
     * @param agentId The CRAFTER agent's ID.
     * @param taskId The task the CRAFTER was working on.
     * @param output The CRAFTER's full text output.
     * @return A structured completion report.
     */
    fun parseCrafterReport(agentId: String, taskId: String, output: String): CompletionReport

    /**
     * Parse a GATE's text output into per-task verdicts.
     *
     * The GATE may review multiple tasks. This method extracts the verdict
     * for each task from the GATE's output.
     *
     * @param gateAgentId The GATE agent's ID.
     * @param output The GATE's full text output.
     * @param tasks The tasks being verified (for matching task-specific sections).
     * @return A map of taskId → verdict parsed from the output.
     */
    fun parseGateVerdicts(
        gateAgentId: String,
        output: String,
        tasks: List<Task>,
    ): Map<String, GateTaskVerdict>
}

/**
 * Parsed verdict for a single task from a GATE agent's output.
 */
data class GateTaskVerdict(
    val verdict: VerificationVerdict,
    val summary: String,
)
