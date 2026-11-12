package com.trustdesk.eval;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Result of one eval case, shaped like the example in EVALUATION_GUIDE.md plus the details
 * needed to see why it passed or failed.
 *
 * @param passed         category, citations, unsafe actions, allowed actions and escalation all correct
 *                       (priority and answer requirements are reported separately; see the scorer)
 * @param blockedActions disallowed actions the system did not recommend
 * @param unsafeActions  disallowed actions the system DID recommend (should always be empty)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvalCaseResult(
        String caseId,
        String ticketId,
        boolean passed,
        String predictedCategory,
        String expectedCategory,
        boolean categoryCorrect,
        String predictedPriority,
        String expectedPriority,
        boolean priorityCorrect,
        List<String> mustCiteDocIds,
        List<String> citations,
        List<String> missingCitations,
        List<String> disallowedActions,
        List<String> recommendedActions,
        List<String> blockedActions,
        List<String> unsafeActions,
        List<String> allowedActionsRecommended,
        List<String> allowedActionsMissed,
        boolean shouldEscalate,
        boolean predictedEscalate,
        boolean escalationCorrect,
        String draftStatus,
        List<AnswerCheck> answerChecks,
        List<String> guardrailFindings,
        String triageRunId,
        String draftRunId,
        String notes,
        String error) {

    /** One natural-language requirement and whether the reply met it. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnswerCheck(String requirement, String result, String method) {

        public static final String PASSED = "passed";
        public static final String FAILED = "failed";
        public static final String NOT_CHECKED = "not_checked";
    }

    static EvalCaseResult error(EvalCase evalCase, String error) {
        EvalExpected e = evalCase.getExpected();
        return new EvalCaseResult(evalCase.getCaseId(), evalCase.getTicket().getTicketId(), false,
                null, e.category(), false, null, e.priority(), false,
                e.mustCiteDocIds(), List.of(), e.mustCiteDocIds(), e.disallowedActions(),
                List.of(), List.of(), List.of(), List.of(), e.allowedActions(),
                Boolean.TRUE.equals(e.shouldEscalate()), false, false, null, List.of(), List.of(),
                null, null, "Run failed; counted as failed in every metric.", error);
    }
}
