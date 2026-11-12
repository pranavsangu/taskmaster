package com.trustdesk.trace;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A tool the AI suggested during a run and what the code did with it.
 *
 * @param outcome {@code recommended} (kept in the draft) or {@code removed} (by validation, policy or escalation)
 * @param reason  the AI's reason when kept, or why it was removed
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolCall(String toolName, String outcome, Boolean requiresHumanApproval, String reason) {

    public static final String RECOMMENDED = "recommended";
    public static final String REMOVED = "removed";
}
