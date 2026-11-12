package com.trustdesk.eval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One evaluation run over some or all eval cases. Created as {@code running}; the background runner
 * adds each case result as it finishes, then stores the metrics and sets {@code completed}.
 */
@Entity
@Table(name = "eval_runs")
public class EvalRun {

    public static final String RUNNING = "running";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";

    @Id
    private String evalRunId;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> caseIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<EvalCaseResult> caseResults = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private EvalMetrics metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<AdversarialCheck> adversarialChecks = new ArrayList<>();

    @Column(nullable = false)
    private String modelProvider;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String promptVersion;

    @Column(columnDefinition = "text")
    private String error;

    /** When the run was requested. */
    @Column(nullable = false)
    private OffsetDateTime queuedAt;

    /** When the eval thread actually started it (runs wait in a queue, one at a time). */
    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    protected EvalRun() {
        // required by JPA
    }

    public EvalRun(String evalRunId, List<String> caseIds, String modelProvider, String modelName,
                   String promptVersion, OffsetDateTime queuedAt) {
        this.evalRunId = evalRunId;
        this.status = RUNNING;
        this.caseIds = new ArrayList<>(caseIds);
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.queuedAt = queuedAt;
    }

    void markStarted(OffsetDateTime now) {
        this.startedAt = now;
    }

    void addCaseResult(EvalCaseResult result) {
        caseResults.add(result);
    }

    void complete(EvalMetrics metrics, List<AdversarialCheck> adversarialChecks, OffsetDateTime now) {
        this.status = COMPLETED;
        this.metrics = metrics;
        this.adversarialChecks = new ArrayList<>(adversarialChecks);
        this.finishedAt = now;
    }

    void fail(String error, OffsetDateTime now) {
        this.status = FAILED;
        this.error = error;
        this.finishedAt = now;
    }

    public String getEvalRunId() {
        return evalRunId;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getCaseIds() {
        return List.copyOf(caseIds);
    }

    public List<EvalCaseResult> getCaseResults() {
        return List.copyOf(caseResults);
    }

    public EvalMetrics getMetrics() {
        return metrics;
    }

    public List<AdversarialCheck> getAdversarialChecks() {
        return List.copyOf(adversarialChecks);
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getError() {
        return error;
    }

    public OffsetDateTime getQueuedAt() {
        return queuedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
