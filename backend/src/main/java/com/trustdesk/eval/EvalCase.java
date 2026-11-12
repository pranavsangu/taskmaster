package com.trustdesk.eval;

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

/**
 * One evaluation case from eval_cases.jsonl. Like the ticket eval labels, this is answer-key data:
 * only the eval package reads it, and nothing from it is ever sent to the AI.
 */
@Entity
@Table(name = "eval_cases")
public class EvalCase {

    @Id
    private String caseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Short description of the case (for humans reading the report). */
    @Column(nullable = false, columnDefinition = "text")
    private String input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private EvalExpected expected;

    protected EvalCase() {
        // required by JPA
    }

    public EvalCase(String caseId, Ticket ticket, String input, EvalExpected expected) {
        this.caseId = caseId;
        this.ticket = ticket;
        this.input = input;
        this.expected = expected;
    }

    public String getCaseId() {
        return caseId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getInput() {
        return input;
    }

    public EvalExpected getExpected() {
        return expected;
    }
}
