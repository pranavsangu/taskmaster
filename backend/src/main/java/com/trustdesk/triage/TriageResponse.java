package com.trustdesk.triage;

import com.trustdesk.guardrail.GuardrailResult;

import java.time.OffsetDateTime;
import java.util.List;

/** Response of the triage endpoints; fields follow the triage example in API_CONTRACT.md. */
public record TriageResponse(
        String triageId,
        String runId,
        String ticketId,
        String category,
        String priority,
        String sentiment,
        boolean shouldEscalate,
        String reasonSummary,
        List<String> retrievedDocIds,
        GuardrailResult guardrail,
        String provider,
        String model,
        OffsetDateTime createdAt) {

    static TriageResponse from(TriageResult result) {
        return new TriageResponse(
                result.getTriageId(),
                result.getRunId(),
                result.getTicket().getTicketId(),
                result.getCategory(),
                result.getPriority(),
                result.getSentiment(),
                result.isShouldEscalate(),
                result.getReasonSummary(),
                result.getRetrievedDocIds(),
                result.getGuardrail(),
                result.getProvider(),
                result.getModel(),
                result.getCreatedAt());
    }
}