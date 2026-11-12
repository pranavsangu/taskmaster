package com.trustdesk.ai;

/**
 * Raw triage output from an AI provider. Values are NOT trusted yet: the triage service
 * checks them against the allowed categories and priorities before anything is stored.
 */
public record TriageSuggestion(
        String category,
        String priority,
        String sentiment,
        boolean shouldEscalate,
        String reasonSummary) {
}