package com.trustdesk.action;

import com.trustdesk.common.ConflictException;
import com.trustdesk.ticket.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A request to run one tool from the catalog for a ticket, and its lifecycle:
 * <pre>
 *   approval_required ──approve──► approved ──execute──► executing ──► executed
 *          │                          ▲                              (or failed)
 *          └──reject──► rejected      └── tools that need no approval start here
 * </pre>
 * The state rules live in this class, so no service can skip a step (e.g. execute before approval).
 * Moving from {@code approved} to {@code executing} is done by one conditional database update
 * ({@link ToolActionRequestRepository#claimForExecution}), so only one request can ever run the tool.
 * {@code idempotency_key} is unique in the database: a retried request can never create a second action.
 * {@code @Version} makes two simultaneous approvals or executions of the same action fail instead of both succeeding.
 */
@Entity
@Table(name = "tool_action_requests")
public class ToolActionRequest {

    public static final String APPROVAL_REQUIRED = "approval_required";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String EXECUTING = "executing";
    public static final String EXECUTED = "executed";
    public static final String FAILED = "failed";

    @Id
    private String actionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private String toolName;

    /** The tool's input fields as sent by the requester, including idempotency_key. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload = new LinkedHashMap<>();

    /** Copied from the tool catalog when the request is made. */
    @Column(nullable = false)
    private String riskLevel;

    /** Copied from the tool catalog when the request is made; never from the requester. */
    private boolean requiresHumanApproval;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestedBy;

    /** What the (simulated) tool returned, e.g. a replacement order ID. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result;

    @Column(columnDefinition = "text")
    private String failureReason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private OffsetDateTime executedAt;

    @Version
    private long version;

    protected ToolActionRequest() {
        // required by JPA
    }

    public ToolActionRequest(String actionId, Ticket ticket, String toolName, Map<String, Object> payload,
                             String riskLevel, boolean requiresHumanApproval, String idempotencyKey,
                             String requestedBy, OffsetDateTime createdAt) {
        this.actionId = actionId;
        this.ticket = ticket;
        this.toolName = toolName;
        this.payload = new LinkedHashMap<>(payload);
        this.riskLevel = riskLevel;
        this.requiresHumanApproval = requiresHumanApproval;
        this.status = requiresHumanApproval ? APPROVAL_REQUIRED : APPROVED;
        this.idempotencyKey = idempotencyKey;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void approve(OffsetDateTime now) {
        requireStatus(APPROVAL_REQUIRED, "approved");
        status = APPROVED;
        updatedAt = now;
    }

    public void reject(OffsetDateTime now) {
        requireStatus(APPROVAL_REQUIRED, "rejected");
        status = REJECTED;
        updatedAt = now;
    }

    public void markExecuted(Map<String, Object> result, OffsetDateTime now) {
        requireStatus(EXECUTING, "marked executed");
        this.status = EXECUTED;
        this.result = new LinkedHashMap<>(result);
        this.executedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String reason, OffsetDateTime now) {
        requireStatus(EXECUTING, "marked failed");
        this.status = FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    /** Throws unless the action may run now. The approval gate for sensitive tools. */
    public void requireExecutable() {
        if (APPROVAL_REQUIRED.equals(status)) {
            throw new ConflictException("Action " + actionId + " requires human approval before it can be executed");
        }
        if (EXECUTING.equals(status)) {
            throw new ConflictException("Action " + actionId + " is already being executed by another request");
        }
        if (!APPROVED.equals(status)) {
            throw new ConflictException("Action " + actionId + " cannot be executed: status is '" + status + "'");
        }
    }

    private void requireStatus(String expected, String target) {
        if (!expected.equals(status)) {
            throw new ConflictException("Action " + actionId + " cannot be " + target + ": status is '" + status + "'");
        }
    }

    public String getActionId() {
        return actionId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getPayload() {
        return Map.copyOf(payload);
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public boolean isRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Map<String, Object> getResult() {
        return result == null ? null : Map.copyOf(result);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }
}