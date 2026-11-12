package com.trustdesk.trace;

import com.trustdesk.guardrail.GuardrailResult;
import com.trustdesk.ticket.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Trace of one AI run (triage or draft): what went in, what the AI suggested, what the code
 * decided, and how it ended. Written for failed runs too. Never updated after saving.
 */
@Entity
@Table(name = "agent_runs")
public class AgentRun {

    public static final String TRIAGE = "triage";
    public static final String DRAFT_REPLY = "draft_reply";
    public static final String EVAL_CASE = "eval_case";
    public static final String FAILED = "failed";

    @Id
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** {@code triage}, {@code draft_reply} or {@code eval_case}. */
    @Column(nullable = false)
    private String runType;

    /** Final status: triage {@code completed}/{@code escalated}; draft {@code generated}/{@code needs_edit}/{@code escalated}; or {@code failed}. */
    @Column(nullable = false)
    private String status;

    /** The stored triage or draft ID; null when the run failed. */
    private String resultId;

    /** Documents shown to the AI, in rank order. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> retrievedDocIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ToolCall> toolCalls = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private GuardrailResult guardrailResults;

    @Column(nullable = false)
    private String modelProvider;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String promptVersion;

    private long latencyMs;

    /** Why the run failed (safe message, no internals); null on success. */
    @Column(columnDefinition = "text")
    private String error;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected AgentRun() {
        // required by JPA
    }

    public AgentRun(String runId, Ticket ticket, String runType, String status, String resultId,
                    List<String> retrievedDocIds, List<ToolCall> toolCalls, GuardrailResult guardrailResults,
                    String modelProvider, String modelName, String promptVersion, long latencyMs, String error,
                    OffsetDateTime createdAt) {
        this.runId = runId;
        this.ticket = ticket;
        this.runType = runType;
        this.status = status;
        this.resultId = resultId;
        this.retrievedDocIds = new ArrayList<>(retrievedDocIds);
        this.toolCalls = new ArrayList<>(toolCalls);
        this.guardrailResults = guardrailResults;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.latencyMs = latencyMs;
        this.error = error;
        this.createdAt = createdAt;
    }

    public String getRunId() {
        return runId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getRunType() {
        return runType;
    }

    public String getStatus() {
        return status;
    }

    public String getResultId() {
        return resultId;
    }

    public List<String> getRetrievedDocIds() {
        return List.copyOf(retrievedDocIds);
    }

    public List<ToolCall> getToolCalls() {
        return List.copyOf(toolCalls);
    }

    public GuardrailResult getGuardrailResults() {
        return guardrailResults;
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

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getError() {
        return error;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
