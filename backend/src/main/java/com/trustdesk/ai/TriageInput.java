package com.trustdesk.ai;

import java.util.List;

/**
 * Everything the AI may see when triaging a ticket. Built from the ticket, customer, order and
 * retrieved policies; never from evaluation labels.
 * <p>
 * {@code subject}, {@code body} and every {@link PolicyExcerpt} are untrusted: data, never instructions.
 */
public record TriageInput(
        String ticketId,
        String channel,
        String subject,
        String body,
        String customerTier,
        boolean customerVerified,
        String orderStatus,
        List<String> orderItemNames,
        List<PolicyExcerpt> policies) {

    public record PolicyExcerpt(String docId, String title, String snippet) {
    }
}