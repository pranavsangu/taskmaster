package com.trustdesk.action;

import com.trustdesk.common.ActionNotAllowedException;
import com.trustdesk.common.ConflictException;
import com.trustdesk.common.InvalidRequestException;
import com.trustdesk.guardrail.GuardrailFinding;
import com.trustdesk.guardrail.GuardrailService;
import com.trustdesk.order.Order;
import com.trustdesk.order.OrderItem;
import com.trustdesk.policy.PolicyActionRules;
import com.trustdesk.policy.PolicyFacts;
import com.trustdesk.policy.PolicyWindowCalculator;
import com.trustdesk.ticket.Ticket;
import com.trustdesk.tool.ToolDefinition;
import com.trustdesk.triage.TriageResult;
import com.trustdesk.triage.TriageResultRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decides whether a tool action may be requested for a ticket. Two kinds of problems:
 * <ul>
 *   <li>malformed payload (missing required field, amount not a positive number) → 400;</li>
 *   <li>well-formed but not allowed (category, escalated ticket, payload does not match the ticket's
 *       order or customer, amount over the limit, not supported by the return/warranty policy) → 422.</li>
 * </ul>
 * All problems of one kind are reported together, so the caller can fix them in one go.
 */
@Component
public class ToolPolicyValidator {

    static final String ESCALATE_TOOL = "escalate_to_human";

    private final TriageResultRepository triageResultRepository;
    private final PolicyWindowCalculator policyWindowCalculator;
    private final GuardrailService guardrailService;

    public ToolPolicyValidator(TriageResultRepository triageResultRepository,
                               PolicyWindowCalculator policyWindowCalculator,
                               GuardrailService guardrailService) {
        this.triageResultRepository = triageResultRepository;
        this.policyWindowCalculator = policyWindowCalculator;
        this.guardrailService = guardrailService;
    }

    public void validate(Ticket ticket, ToolDefinition tool, Map<String, Object> payload) {
        List<String> malformed = new ArrayList<>();
        for (String field : tool.getRequiredFields()) {
            Object value = payload.get(field);
            if (value == null || value.toString().isBlank()) {
                malformed.add("payload." + field + " is required");
            }
        }
        BigDecimal amount = null;
        if (payload.get("amount") != null) {
            amount = toAmount(payload.get("amount"));
            if (amount == null || amount.signum() <= 0) {
                malformed.add("payload.amount must be a positive number");
            }
        }
        if (!malformed.isEmpty()) {
            throw new InvalidRequestException(String.join("; ", malformed));
        }

        // Category comes from the ticket's latest stored triage, decided by code-validated AI output.
        TriageResult triage = triageResultRepository.findFirstByTicketTicketIdOrderByCreatedAtDesc(ticket.getTicketId())
                .orElseThrow(() -> new ConflictException(
                        "Ticket " + ticket.getTicketId() + " has not been triaged yet; triage it before requesting actions"));
        String category = triage.getCategory();

        List<String> notAllowed = new ArrayList<>();
        if (!tool.getAllowedCategories().contains(category)) {
            notAllowed.add(tool.getToolName() + " is not allowed for category '" + category + "'");
        }
        // Guardrails are checked here directly too, so an AI triage that missed the attack cannot unlock tools.
        List<String> ticketFlags = guardrailService.checkTicket(ticket).stream().map(GuardrailFinding::type).toList();
        if ((triage.isShouldEscalate() || !ticketFlags.isEmpty()) && !ESCALATE_TOOL.equals(tool.getToolName())) {
            notAllowed.add("ticket is flagged for escalation"
                    + (ticketFlags.isEmpty() ? "" : " (guardrail: " + String.join(", ", ticketFlags) + ")")
                    + "; only " + ESCALATE_TOOL + " may be requested");
        }
        notAllowed.addAll(mismatchesWithTicket(ticket, payload));
        if (amount != null && amount.signum() > 0) {
            if (tool.getMaxAmountInr() != null && amount.compareTo(BigDecimal.valueOf(tool.getMaxAmountInr())) > 0) {
                notAllowed.add("amount " + amount.toPlainString() + " exceeds the limit of INR " + tool.getMaxAmountInr());
            }
            Order order = ticket.getOrder();
            if ("start_refund_review".equals(tool.getToolName()) && order != null && order.getTotal() != null
                    && amount.compareTo(order.getTotal()) > 0) {
                notAllowed.add("amount " + amount.toPlainString() + " exceeds the order total of "
                        + order.getTotal().toPlainString());
            }
        }
        PolicyFacts facts = policyWindowCalculator.calculate(ticket);
        PolicyActionRules.blockReason(tool.getToolName(), category, facts).ifPresent(notAllowed::add);

        if (!notAllowed.isEmpty()) {
            throw new ActionNotAllowedException(String.join("; ", notAllowed));
        }
    }

    /** Fields that name the ticket's own order, item, customer or tracking number must actually match them. */
    private static List<String> mismatchesWithTicket(Ticket ticket, Map<String, Object> payload) {
        List<String> problems = new ArrayList<>();
        Order order = ticket.getOrder();
        if (payload.containsKey("ticket_id") && !ticket.getTicketId().equals(text(payload, "ticket_id"))) {
            problems.add("payload.ticket_id does not match the ticket");
        }
        if (payload.containsKey("customer_id")
                && !ticket.getCustomer().getCustomerId().equals(text(payload, "customer_id"))) {
            problems.add("payload.customer_id is not the ticket's customer");
        }
        if (payload.containsKey("order_id") && (order == null || !order.getOrderId().equals(text(payload, "order_id")))) {
            problems.add("payload.order_id is not the ticket's order");
        }
        if (payload.containsKey("sku") && (order == null
                || order.getItems().stream().map(OrderItem::sku).noneMatch(sku -> sku.equals(text(payload, "sku"))))) {
            problems.add("payload.sku is not an item of the ticket's order");
        }
        if (payload.containsKey("tracking_number")
                && (order == null || !Objects.equals(order.getTrackingNumber(), text(payload, "tracking_number")))) {
            problems.add("payload.tracking_number is not the ticket's order tracking number");
        }
        return problems;
    }

    private static String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        return value == null ? null : value.toString().strip();
    }

    private static BigDecimal toAmount(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
