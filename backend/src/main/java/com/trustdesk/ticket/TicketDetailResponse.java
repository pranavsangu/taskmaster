package com.trustdesk.ticket;

import com.trustdesk.order.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A ticket with its customer and order context. Deliberately contains no evaluation labels.
 * The subject and body are returned unchanged (original customer text, for auditability).
 */
public record TicketDetailResponse(
        String ticketId,
        String channel,
        String subject,
        String body,
        String status,
        OffsetDateTime createdAt,
        CustomerContext customer,
        OrderContext order) {

    public record CustomerContext(
            String customerId,
            String name,
            String email,
            String tier,
            String country,
            boolean verified,
            List<String> tags,
            LocalDate createdAt) {
    }

    /** {@code null} in the response when the ticket has no linked order. */
    public record OrderContext(
            String orderId,
            String status,
            LocalDate placedAt,
            LocalDate deliveredAt,
            LocalDate eligibleReturnUntil,
            BigDecimal total,
            String currency,
            String paymentStatus,
            String trackingNumber,
            List<OrderItem> items) {
    }
}