package com.trustdesk.draft;

import com.trustdesk.guardrail.GuardrailResult;
import com.trustdesk.policy.PolicyFacts;
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
 * One stored draft reply, after validation. A ticket can have many drafts; the newest is the
 * current one. Statuses: {@code generated} (normal draft), {@code needs_edit} (guardrails found
 * wording a reviewer must fix before sending) or {@code escalated} (needs a human specialist; the
 * only recommended action is escalate_to_human).
 */
@Entity
@Table(name = "draft_replies")
public class DraftReply {

    @Id
    private String draftId;

    /** The agent run (trace) that produced this draft. */
    private String runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** The triage whose category and escalation flag this draft was validated against. */
    @Column(nullable = false)
    private String triageId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** Document IDs cited in the reply; always a subset of {@link #retrievedDocIds}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> citations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<RecommendedAction> recommendedActions = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String escalationReason;

    /** What validation removed or replaced from the AI's suggestion, for reviewers and audits. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> validationNotes = new ArrayList<>();

    /** Knowledge-base documents shown to the AI, in rank order. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> retrievedDocIds = new ArrayList<>();

    /** Return/warranty windows calculated from the ticket date, as given to the AI and the validator. */
    @JdbcTypeCode(SqlTypes.JSON)
    private PolicyFacts policyFacts;

    /** What the guardrails found in the ticket, the retrieved documents and the reply. */
    @JdbcTypeCode(SqlTypes.JSON)
    private GuardrailResult guardrail;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected DraftReply() {
        // required by JPA
    }

    public DraftReply(String draftId, String runId, Ticket ticket, String triageId, String status, String body,
                      List<String> citations, List<RecommendedAction> recommendedActions, String escalationReason,
                      List<String> validationNotes, List<String> retrievedDocIds, PolicyFacts policyFacts,
                      GuardrailResult guardrail, String provider, String model, OffsetDateTime createdAt) {
        this.draftId = draftId;
        this.runId = runId;
        this.ticket = ticket;
        this.triageId = triageId;
        this.status = status;
        this.body = body;
        this.citations = new ArrayList<>(citations);
        this.recommendedActions = new ArrayList<>(recommendedActions);
        this.escalationReason = escalationReason;
        this.validationNotes = new ArrayList<>(validationNotes);
        this.retrievedDocIds = new ArrayList<>(retrievedDocIds);
        this.policyFacts = policyFacts;
        this.guardrail = guardrail;
        this.provider = provider;
        this.model = model;
        this.createdAt = createdAt;
    }

    public String getDraftId() {
        return draftId;
    }

    public String getRunId() {
        return runId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getTriageId() {
        return triageId;
    }

    public String getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public List<String> getCitations() {
        return List.copyOf(citations);
    }

    public List<RecommendedAction> getRecommendedActions() {
        return List.copyOf(recommendedActions);
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public List<String> getValidationNotes() {
        return List.copyOf(validationNotes);
    }

    public List<String> getRetrievedDocIds() {
        return List.copyOf(retrievedDocIds);
    }

    public PolicyFacts getPolicyFacts() {
        return policyFacts;
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