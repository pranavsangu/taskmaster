package com.trustdesk.action;

import com.trustdesk.common.ConflictException;
import com.trustdesk.customer.Customer;
import com.trustdesk.order.Order;
import com.trustdesk.order.OrderItem;
import com.trustdesk.policy.PolicyWindowCalculator;
import com.trustdesk.ticket.Ticket;
import com.trustdesk.tool.ToolDefinition;
import com.trustdesk.triage.TriageResultRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test with a fake triage repository: cases the seeded database cannot set up reliably. */
class ToolPolicyValidatorTest {

    @Test
    void untriagedTicketCannotRequestActions() {
        TriageResultRepository triages = mock(TriageResultRepository.class);
        when(triages.findFirstByTicketTicketIdOrderByCreatedAtDesc("tkt_1")).thenReturn(Optional.empty());
        ToolPolicyValidator validator = new ToolPolicyValidator(triages, new PolicyWindowCalculator("Asia/Kolkata"),
                new com.trustdesk.guardrail.GuardrailService());

        Customer customer = new Customer("cus_1", "Test", "t@example.com", "standard", "IN",
                LocalDate.of(2024, 1, 1), true, List.of());
        Order order = new Order("ord_1", customer, "in_transit", LocalDate.of(2026, 6, 22), null, null,
                new BigDecimal("2499"), "INR", "paid", "TRK1",
                List.of(new OrderItem("SKU", "Case", 1, "accessory", false)));
        Ticket ticket = new Ticket("tkt_1", customer, order, "email", "s", "b",
                OffsetDateTime.parse("2026-07-02T12:00:00+05:30"), "open");
        ToolDefinition tool = new ToolDefinition("open_carrier_investigation", "d", "low", false,
                List.of("shipping"), List.of("order_id", "tracking_number", "reason", "idempotency_key"), null);

        assertThatThrownBy(() -> validator.validate(ticket, tool, Map.of("order_id", "ord_1",
                "tracking_number", "TRK1", "reason", "x", "idempotency_key", "k")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has not been triaged yet");
    }
}
