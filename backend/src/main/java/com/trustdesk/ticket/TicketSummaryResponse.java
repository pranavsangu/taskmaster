package com.trustdesk.ticket;

import java.time.OffsetDateTime;

/** One row in the ticket list. Deliberately contains no evaluation labels. */
public record TicketSummaryResponse(
        String ticketId,
        String subject,
        String channel,
        String status,
        OffsetDateTime createdAt,
        String customerId,
        String customerName,
        String orderId) {
}