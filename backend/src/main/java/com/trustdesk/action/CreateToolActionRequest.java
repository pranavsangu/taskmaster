package com.trustdesk.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body of POST /tool-actions, shaped like the example in API_CONTRACT.md.
 * The idempotency key goes inside {@code payload}, as in the contract.
 */
public record CreateToolActionRequest(
        @NotBlank(message = "ticket_id is required")
        String ticketId,

        @NotBlank(message = "tool_name is required")
        String toolName,

        @NotNull(message = "payload is required")
        Map<String, Object> payload,

        /** Optional; who asked for the action. Defaults to "support_agent". */
        @Size(max = 100, message = "requested_by must be at most 100 characters")
        String requestedBy) {
}
