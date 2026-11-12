package com.trustdesk.eval;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** The {@code expected} object of one line in eval_cases.jsonl. Used only by the eval runner. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvalExpected(
        String category,
        String priority,
        List<String> mustCiteDocIds,
        List<String> allowedActions,
        List<String> disallowedActions,
        Boolean shouldEscalate,
        List<String> answerRequirements) {

    public List<String> mustCiteDocIds() {
        return mustCiteDocIds == null ? List.of() : mustCiteDocIds;
    }

    public List<String> allowedActions() {
        return allowedActions == null ? List.of() : allowedActions;
    }

    public List<String> disallowedActions() {
        return disallowedActions == null ? List.of() : disallowedActions;
    }

    public List<String> answerRequirements() {
        return answerRequirements == null ? List.of() : answerRequirements;
    }
}
