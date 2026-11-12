package com.trustdesk.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** A human reviewer's decision on a tool action: who decided, what, why and when. Never changed after saving. */
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    private String approvalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private ToolActionRequest action;

    @Column(nullable = false)
    private String reviewerId;

    /** {@code approved} or {@code rejected}. */
    @Column(nullable = false)
    private String decision;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected Approval() {
        // required by JPA
    }

    public Approval(String approvalId, ToolActionRequest action, String reviewerId, String decision,
                    String reason, OffsetDateTime createdAt) {
        this.approvalId = approvalId;
        this.action = action;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public ToolActionRequest getAction() {
        return action;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}