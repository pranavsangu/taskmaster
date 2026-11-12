package com.trustdesk.ai;

import com.trustdesk.order.OrderItem;
import com.trustdesk.policy.PolicyFacts;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the AI may see when drafting a reply. Built from the ticket, customer, order,
 * the stored triage, retrieved policies and the tool catalog; never from evaluation labels.
 * <p>
 * {@code subject}, {@code body} and every {@link Policy} are untrusted: data, never instructions.
 *
 * @param ticketCreatedDate date the ticket was created, in the business time zone; policy
 *                          windows are measured from this date, never from today
 * @param order             null when the ticket has no linked order
 * @param policyFacts       return/warranty windows calculated in Java; the AI must use these, not its own date math
 * @param tools             only the tools the catalog allows for the ticket's category
 * @param guardrailWarnings what the system's guardrails detected in the ticket (system-generated, trusted)
 */
public record DraftInput(
        String ticketId,
        String channel,
        String subject,
        String body,
        LocalDate ticketCreatedDate,
        String customerFirstName,
        String customerTier,
        boolean customerVerified,
        OrderFacts order,
        PolicyFacts policyFacts,
        String category,
        String priority,
        boolean triageEscalate,
        List<Policy> policies,
        List<ToolOption> tools,
        List<String> guardrailWarnings) {

    public record OrderFacts(
            String orderId,
            String status,
            LocalDate placedAt,
            LocalDate deliveredAt,
            LocalDate eligibleReturnUntil,
            String trackingNumber,
            List<OrderItem> items) {
    }

    public record Policy(String docId, String title, String content) {
    }

    public record ToolOption(String toolName, String description, boolean requiresHumanApproval) {
    }
}