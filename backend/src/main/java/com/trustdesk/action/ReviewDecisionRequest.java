package com.trustdesk.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /tool-actions/{id}/approve and /reject. Both fields are required for the audit trail. */
public record ReviewDecisionRequest(
        @NotBlank(message = "reviewer_id is required")
        @Size(max = 100, message = "reviewer_id must be at most 100 characters")
        String reviewerId,

        @NotBlank(message = "reason is required")
        @Size(max = 1000, message = "reason must be at most 1000 characters")
        String reason) {
}
