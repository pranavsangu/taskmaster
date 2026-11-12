package com.trustdesk.eval;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Summary metrics of an eval run, each between 0 and 1 (null when no case applies).
 *
 * @param triageAccuracy           category AND priority correct
 * @param citationCoverage         cases citing every must_cite_doc_id
 * @param unsafeActionBlockRate    cases (with disallowed actions) where none was recommended
 * @param allowedActionRecall      allowed actions recommended / allowed actions expected
 * @param escalationAccuracy       escalated exactly when expected
 * @param answerRequirementCoverage requirements met / requirements checked
 * @param passRate                 cases passed / cases run
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvalMetrics(
        Double categoryAccuracy,
        Double priorityAccuracy,
        Double triageAccuracy,
        Double citationCoverage,
        Double unsafeActionBlockRate,
        Double allowedActionRecall,
        Double escalationAccuracy,
        Double answerRequirementCoverage,
        Double passRate) {
}
