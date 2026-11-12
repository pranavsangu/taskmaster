package com.trustdesk.triage;

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
 * One stored triage of a ticket, after validation. A ticket can be triaged many times;
 * the newest row is its current triage. Older rows are kept for auditability.
 */
@Entity
@Table(name = "triage_results")
public class TriageResult {

    @Id
    private String triageId;

    /** The agent run (trace) that produced this triage. */
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private String sentiment;

    private boolean shouldEscalate;

    @Column(nullable = false, columnDefinition = "text")
    private String reasonSummary;

    /** Knowledge-base documents shown to the AI, in rank order. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> retrievedDocIds = new ArrayList<>();

    /** What the guardrails found in the ticket and the retrieved documents. */
    @JdbcTypeCode(SqlTypes.JSON)
    private GuardrailResult guardrail;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected TriageResult() {
        // required by JPA
    }

    public TriageResult(String triageId, String runId, Ticket ticket, String category, String priority, String sentiment,
                        boolean shouldEscalate, String reasonSummary, List<String> retrievedDocIds,
                        GuardrailResult guardrail, String provider, String model, OffsetDateTime createdAt) {
        this.triageId = triageId;
        this.runId = runId;
        this.ticket = ticket;
        this.category = category;
        this.priority = priority;
        this.sentiment = sentiment;
        this.shouldEscalate = shouldEscalate;
        this.reasonSummary = reasonSummary;
        this.retrievedDocIds = new ArrayList<>(retrievedDocIds);
        this.guardrail = guardrail;
        this.provider = provider;
        this.model = model;
        this.createdAt = createdAt;
    }

    public String getTriageId() {
        return triageId;
    }

    public String getRunId() {
        return runId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }

    public String getSentiment() {
        return sentiment;
    }

    public boolean isShouldEscalate() {
        return shouldEscalate;
    }

    public String getReasonSummary() {
        return reasonSummary;
    }

    public List<String> getRetrievedDocIds() {
        return List.copyOf(retrievedDocIds);
    }

    public GuardrailResult getGuardrail() {
        return guardrail;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
