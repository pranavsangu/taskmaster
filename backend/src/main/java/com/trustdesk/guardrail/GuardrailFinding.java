package com.trustdesk.guardrail;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One thing a guardrail check noticed.
 *
 * @param type   e.g. {@code prompt_injection}, {@code untrusted_document}, {@code action_claim}
 * @param source where it was found: {@code ticket}, a document ID, or {@code draft}
 * @param detail the matching text (shortened) or an explanation
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GuardrailFinding(String type, String source, String detail) {

    public static final String PROMPT_INJECTION = "prompt_injection";
    public static final String SECRET_REQUEST = "secret_request";
    public static final String VERIFICATION_BYPASS = "verification_bypass";
    public static final String UNTRUSTED_DOCUMENT = "untrusted_document";
    public static final String SECRET_LEAK = "secret_leak";
    public static final String ACTION_CLAIM = "action_claim";
    public static final String REMOVED_ACTION_MENTION = "removed_action_mention";
    public static final String PLACEHOLDER_FIXED = "placeholder_fixed";
}
