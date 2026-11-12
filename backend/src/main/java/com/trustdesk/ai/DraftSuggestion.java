package com.trustdesk.ai;

import java.util.List;

/**
 * Raw draft output from an AI provider. Values are NOT trusted yet: the draft service keeps
 * only citations of retrieved documents and actions the tool catalog allows, and decides
 * escalation itself.
 */
public record DraftSuggestion(
        String body,
        List<String> citations,
        List<ActionSuggestion> recommendedActions,
        boolean shouldEscalate,
        String escalationReason) {

    public record ActionSuggestion(String toolName, String reason) {
    }
}