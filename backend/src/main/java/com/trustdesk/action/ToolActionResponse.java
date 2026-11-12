package com.trustdesk.action;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response of the tool-action endpoints.
 *
 * @param idempotentReplay true when this response returns an action created by an earlier request
 *                         with the same idempotency key (nothing new was created)
 */
public record ToolActionResponse(
        String actionId,
        String ticketId,
        String toolName,
        Map<String, Object> payload,
        String riskLevel,
        boolean requiresHumanApproval,
        String status,
        String idempotencyKey,
        String requestedBy,
        Map<String, Object> result,
        String failureReason,
        List<ApprovalResponse> approvals,
        boolean idempotentReplay,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime executedAt) {

    public record ApprovalResponse(String approvalId, String reviewerId, String decision, String reason,
                                   OffsetDateTime createdAt) {

        static ApprovalResponse from(Approval approval) {
            return new ApprovalResponse(approval.getApprovalId(), approval.getReviewerId(), approval.getDecision(),
                    approval.getReason(), approval.getCreatedAt());
        }
    }

    static ToolActionResponse from(ToolActionRequest action, List<Approval> approvals, boolean idempotentReplay) {
        return new ToolActionResponse(
                action.getActionId(),
                action.getTicket().getTicketId(),
                action.getToolName(),
                action.getPayload(),
                action.getRiskLevel(),
                action.isRequiresHumanApproval(),
                action.getStatus(),
                action.getIdempotencyKey(),
                action.getRequestedBy(),
                action.getResult(),
                action.getFailureReason(),
                approvals.stream().map(ApprovalResponse::from).toList(),
                idempotentReplay,
                action.getCreatedAt(),
                action.getUpdatedAt(),
                action.getExecutedAt());
    }
}
