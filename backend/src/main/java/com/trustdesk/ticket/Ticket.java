package com.trustdesk.ticket;

import com.trustdesk.customer.Customer;
import com.trustdesk.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A support ticket. The subject and body are untrusted customer input: data, never instructions.
 * Expected labels for evaluation live in {@link TicketEvalLabel}, not here.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private String ticketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false, columnDefinition = "text")
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** Policy windows (returns, warranty) are calculated from this, never from today's date. */
    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private String status;

    protected Ticket() {
        // required by JPA
    }

    public Ticket(String ticketId, Customer customer, Order order, String channel, String subject,
                  String body, OffsetDateTime createdAt, String status) {
        this.ticketId = ticketId;
        this.customer = customer;
        this.order = order;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Order getOrder() {
        return order;
    }

    public String getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }
}