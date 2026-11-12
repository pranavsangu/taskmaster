package com.trustdesk.eval;

import com.trustdesk.ai.AiProvider;
import com.trustdesk.common.ConflictException;
import com.trustdesk.common.InvalidRequestException;
import com.trustdesk.common.ResourceNotFoundException;
import com.trustdesk.draft.DraftResponse;
import com.trustdesk.draft.DraftService;
import com.trustdesk.guardrail.GuardrailResult;
import com.trustdesk.trace.AgentRun;
import com.trustdesk.trace.AgentRunRecorder;
import com.trustdesk.trace.ToolCall;
import com.trustdesk.triage.TriageResponse;
import com.trustdesk.triage.TriageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs eval cases through the <b>real</b> triage and draft workflows (same code, same AI provider
 * as the API), scores them, and stores the report. Runs in the background on {@code evalExecutor}.
 * <p>
 * The expected values are read here and only here; the workflows being evaluated never see them.
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final TriageService triageService;
    private final DraftService draftService;
    private final AiProvider aiProvider;
    private final AgentRunRecorder agentRunRecorder;
    private final ThreadPoolTaskExecutor evalExecutor;
    private final long pauseBetweenCasesMs;
    private final ZoneId businessZone;
    private final EvalScorer scorer = new EvalScorer();

    public EvalService(EvalCaseRepository evalCaseRepository,
                       EvalRunRepository evalRunRepository,
                       TriageService triageService,
                       DraftService draftService,
                       AiProvider aiProvider,
                       AgentRunRecorder agentRunRecorder,
                       @Qualifier("evalExecutor") ThreadPoolTaskExecutor evalExecutor,
                       @Value("${trustdesk.eval.pause-between-cases-ms:0}") long pauseBetweenCasesMs,
                       @Value("${trustdesk.business-time-zone}") String businessZone) {
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.triageService = triageService;
        this.draftService = draftService;
        this.aiProvider = aiProvider;
        this.agentRunRecorder = agentRunRecorder;
        this.evalExecutor = evalExecutor;
        this.pauseBetweenCasesMs = pauseBetweenCasesMs;
        this.businessZone = ZoneId.of(businessZone);
    }

    /** Creates the run and queues it; returns at once with status {@code running}. */
    public EvalRunResponse start(CreateEvalRunRequest request) {
        List<String> allIds = evalCaseRepository.findAllByOrderByCaseIdAsc().stream().map(EvalCase::getCaseId).toList();
        List<String> requested = request == null || request.caseIds() == null || request.caseIds().isEmpty()
                ? allIds : request.caseIds().stream().distinct().toList();
        List<String> unknown = requested.stream().filter(id -> !allIds.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new InvalidRequestException("Unknown case_ids: " + String.join(", ", unknown));
        }
        List<String> caseIds = allIds.stream().filter(requested::contains).toList();

        EvalRun run = evalRunRepository.save(new EvalRun("eval_run_" + UUID.randomUUID(), caseIds,
                aiProvider.name(), aiProvider.model(), aiProvider.promptVersion(), now()));
        try {
            evalExecutor.execute(() -> execute(run.getEvalRunId()));
        } catch (TaskRejectedException e) {
            evalRunRepository.delete(run);
            throw new ConflictException("Too many eval runs are queued; try again when one has finished");
        }
        return EvalRunResponse.from(run);
    }

    public EvalRunResponse get(String evalRunId) {
        return EvalRunResponse.from(find(evalRunId));
    }

    public List<EvalRunResponse> list() {
        return evalRunRepository.findAllByOrderByQueuedAtDesc().stream().map(EvalRunResponse::from).toList();
    }

    /** Runs on the eval thread. Each case result is saved as soon as it is known, so progress is visible. */
    void execute(String evalRunId) {
        try {
            EvalRun started = find(evalRunId);
            started.markStarted(now());
            evalRunRepository.save(started);
            Set<String> wanted = Set.copyOf(started.getCaseIds());
            List<EvalCase> cases = evalCaseRepository.findAllByOrderByCaseIdAsc().stream()
                    .filter(c -> wanted.contains(c.getCaseId()))
                    .toList();
            List<EvalCaseResult> results = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                if (i > 0 && pauseBetweenCasesMs > 0) {
                    Thread.sleep(pauseBetweenCasesMs);
                }
                EvalCaseResult result = runCase(evalRunId, cases.get(i));
                results.add(result);
                EvalRun run = find(evalRunId);
                run.addCaseResult(result);
                evalRunRepository.save(run);
            }
            EvalRun run = find(evalRunId);
            run.complete(scorer.aggregate(results), scorer.adversarialChecks(results), now());
            evalRunRepository.save(run);
            log.info("Eval run {} completed: {}/{} cases passed", evalRunId,
                    results.stream().filter(EvalCaseResult::passed).count(), results.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failRun(evalRunId, "Eval run was interrupted");
        } catch (RuntimeException e) {
            log.error("Eval run {} failed", evalRunId, e);
            failRun(evalRunId, "Eval run failed: " + AgentRunRecorder.safeError(e));
        }
    }

    private EvalCaseResult runCase(String evalRunId, EvalCase evalCase) {
        long start = System.nanoTime();
        String ticketId = evalCase.getTicket().getTicketId();
        EvalCaseResult result;
        DraftResponse draft = null;
        try {
            // A fresh triage every time, so the result reflects the current prompts and model.
            TriageResponse triage = triageService.triage(ticketId);
            draft = draftService.draft(ticketId);
            result = scorer.score(evalCase, triage, draft);
        } catch (RuntimeException e) {
            log.warn("Eval case {} failed: {}", evalCase.getCaseId(), e.getMessage());
            result = EvalCaseResult.error(evalCase, AgentRunRecorder.safeError(e));
        }
        agentRunRecorder.record(new AgentRun(
                "run_" + UUID.randomUUID(),
                evalCase.getTicket(),
                AgentRun.EVAL_CASE,
                result.error() != null ? AgentRun.FAILED : (result.passed() ? "passed" : "not_passed"),
                evalRunId,
                draft == null ? List.of() : draft.retrievedDocIds(),
                draft == null ? List.of() : draft.recommendedActions().stream()
                        .map(a -> new ToolCall(a.toolName(), ToolCall.RECOMMENDED, a.requiresHumanApproval(), a.reason()))
                        .toList(),
                draft == null ? GuardrailResult.of(List.of()) : draft.guardrail(),
                aiProvider.name(),
                aiProvider.model(),
                aiProvider.promptVersion(),
                (System.nanoTime() - start) / 1_000_000,
                result.error(),
                now()));
        return result;
    }

    private void failRun(String evalRunId, String error) {
        evalRunRepository.findById(evalRunId).ifPresent(run -> {
            run.fail(error, now());
            evalRunRepository.save(run);
        });
    }

    private EvalRun find(String evalRunId) {
        return evalRunRepository.findById(evalRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Eval run " + evalRunId + " not found"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(businessZone);
    }
}
