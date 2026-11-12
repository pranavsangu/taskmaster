package com.trustdesk.draft;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A tool action recommended in a draft, after validation. {@code requiresHumanApproval} is
 * copied from the tool catalog, never from the AI. Stored inside the draft's jsonb column.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecommendedAction(String toolName, boolean requiresHumanApproval, String reason) {
}
