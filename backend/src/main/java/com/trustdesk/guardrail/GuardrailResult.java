package com.trustdesk.guardrail;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Everything the guardrails noticed during one triage or draft run. Stored as jsonb with the result.
 *
 * @param status {@code passed} when nothing was found, otherwise {@code flagged}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GuardrailResult(String status, List<GuardrailFinding> findings) {

    public static GuardrailResult of(List<GuardrailFinding> findings) {
        return new GuardrailResult(findings.isEmpty() ? "passed" : "flagged", List.copyOf(findings));
    }

    public boolean has(String type) {
        return findings.stream().anyMatch(f -> f.type().equals(type));
    }
}
