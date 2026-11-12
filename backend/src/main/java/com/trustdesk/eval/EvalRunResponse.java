package com.trustdesk.eval;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response of the eval-run endpoints. The headline metrics sit at the top level, like the example
 * in API_CONTRACT.md; per-case results and the adversarial verdicts follow.
 */
public record EvalRunResponse(
        String evalRunId,
        String status,
        int totalCases,
        int completedCases,
        long passedCases,
        Double triageAccuracy,
        Double categoryAccuracy,
        Double priorityAccuracy,
        Double citationCoverage,
        Double unsafeActionBlockRate,
        Double allowedActionRecall,
        Double escalationAccuracy,
        Double answerRequirementCoverage,
        Double passRate,
        List<AdversarialCheck> adversarialChecks,
        List<EvalCaseResult> caseResults,
        String modelProvider,
        String modelName,
        String promptVersion,
        String error,
        OffsetDateTime queuedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt) {

    static EvalRunResponse from(EvalRun run) {
        EvalMetrics m = run.getMetrics();
        List<EvalCaseResult> results = run.getCaseResults();
        return new EvalRunResponse(
                run.getEvalRunId(),
                run.getStatus(),
                run.getCaseIds().size(),
                results.size(),
                results.stream().filter(EvalCaseResult::passed).count(),
                m == null ? null : m.triageAccuracy(),
                m == null ? null : m.categoryAccuracy(),
                m == null ? null : m.priorityAccuracy(),
                m == null ? null : m.citationCoverage(),
                m == null ? null : m.unsafeActionBlockRate(),
                m == null ? null : m.allowedActionRecall(),
                m == null ? null : m.escalationAccuracy(),
                m == null ? null : m.answerRequirementCoverage(),
                m == null ? null : m.passRate(),
                run.getAdversarialChecks(),
                results,
                run.getModelProvider(),
                run.getModelName(),
                run.getPromptVersion(),
                run.getError(),
                run.getQueuedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
