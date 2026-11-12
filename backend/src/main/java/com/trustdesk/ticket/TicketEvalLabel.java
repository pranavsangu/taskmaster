package com.trustdesk.ticket;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * The expected triage answers for a seed ticket. Only the evaluation runner may read these;
 * triage and drafting logic must never depend on them.
 */
@Entity
@Table(name = "ticket_eval_labels")
public class TicketEvalLabel {

    /** Same value as the ticket's ID; filled in by Hibernate from {@link #ticket} (@MapsId). */
    @Id
    private String ticketId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    private String expectedCategory;

    private String expectedPriority;

    private String expectedSentiment;

    private boolean expectedEscalation;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> expectedActions = new ArrayList<>();

    protected TicketEvalLabel() {
        // required by JPA
    }

    public TicketEvalLabel(Ticket ticket) {
        this.ticket = ticket;
    }

    public void update(String expectedCategory, String expectedPriority, String expectedSentiment,
                       boolean expectedEscalation, List<String> expectedActions) {
        this.expectedCategory = expectedCategory;
        this.expectedPriority = expectedPriority;
        this.expectedSentiment = expectedSentiment;
        this.expectedEscalation = expectedEscalation;
        this.expectedActions = expectedActions == null ? new ArrayList<>() : new ArrayList<>(expectedActions);
    }

    public String getTicketId() {
        return ticketId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getExpectedCategory() {
        return expectedCategory;
    }

    public String getExpectedPriority() {
        return expectedPriority;
    }

    public String getExpectedSentiment() {
        return expectedSentiment;
    }

    public boolean isExpectedEscalation() {
        return expectedEscalation;
    }

    public List<String> getExpectedActions() {
        return List.copyOf(expectedActions);
    }
}