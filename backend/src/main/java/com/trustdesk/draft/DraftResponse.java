package com.trustdesk.draft;

import com.trustdesk.guardrail.GuardrailResult;
import com.trustdesk.policy.PolicyFacts;

import java.time.OffsetDateTime;
import java.util.List;

/** Response of the draft-reply endpoints; fields follow the draft example in API_CONTRACT.md. */
public record DraftResponse(
        String draftId,
        String runId,
        String ticketId,
        String triageId,
        String status,
        String body,
        List<String> citations,
        List<RecommendedAction> recommendedActions,
        String escalationReason,
        List<String> validationNotes,
        List<String> retrievedDocIds,
        PolicyFacts policyFacts,
        GuardrailResult guardrail,
        String provider,
        String model,
        OffsetDateTime createdAt) {

    static DraftResponse from(DraftReply draft) {
        return new DraftResponse(
                draft.getDraftId(),
                draft.getRunId(),
                draft.getTicket().getTicketId(),
                draft.getTriageId(),
                draft.getStatus(),
                draft.getBody(),
                draft.getCitations(),
                draft.getRecommendedActions(),
                draft.getEscalationReason(),
                draft.getValidationNotes(),
                draft.getRetrievedDocIds(),
                draft.getPolicyFacts(),
                draft.getGuardrail(),
                draft.getProvider(),
                draft.getModel(),
                draft.getCreatedAt());
    }
}