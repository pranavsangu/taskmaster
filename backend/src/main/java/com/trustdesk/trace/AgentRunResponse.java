package com.trustdesk.trace;

import com.trustdesk.guardrail.GuardrailResult;

import java.time.OffsetDateTime;
import java.util.List;

/** Response of the agent-run endpoints; field names follow docs/DATA_MODEL.md (Agent Run Trace). */
public record AgentRunResponse(
        String runId,
        String ticketId,
        String runType,
        String status,
        String resultId,
        List<String> retrievedDocIds,
        List<ToolCall> toolCalls,
        GuardrailResult guardrailResults,
        String modelProvider,
        String modelName,
        String promptVersion,
        long latencyMs,
        String error,
        OffsetDateTime createdAt) {

    static AgentRunResponse from(AgentRun run) {
        return new AgentRunResponse(
                run.getRunId(),
                run.getTicket().getTicketId(),
                run.getRunType(),
                run.getStatus(),
                run.getResultId(),
                run.getRetrievedDocIds(),
                run.getToolCalls(),
                run.getGuardrailResults(),
                run.getModelProvider(),
                run.getModelName(),
                run.getPromptVersion(),
                run.getLatencyMs(),
                run.getError(),
                run.getCreatedAt());
    }
}
