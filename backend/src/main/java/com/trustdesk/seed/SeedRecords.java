package com.trustdesk.seed;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.trustdesk.eval.EvalExpected;
import com.trustdesk.order.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Java shapes that mirror the seed JSON files field for field (snake_case in JSON,
 * camelCase in Java). The loader converts these into entities.
 */
final class SeedRecords {

    private SeedRecords() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CustomerSeed(String customerId, String name, String email, String tier, String country,
                        LocalDate createdAt, boolean verified, List<String> tags) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OrderSeed(String orderId, String customerId, String status, LocalDate placedAt,
                     LocalDate deliveredAt, LocalDate eligibleReturnUntil, BigDecimal total,
                     String currency, String paymentStatus, String trackingNumber, List<OrderItem> items) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TicketSeed(String ticketId, String customerId, String orderId, String channel, String subject,
                      String body, OffsetDateTime createdAt, String status,
                      String expectedCategory, String expectedPriority, String expectedSentiment,
                      boolean expectedEscalation, List<String> expectedActions) {
    }

    /** One line of eval_cases.jsonl. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record EvalCaseSeed(String caseId, String ticketId, String input, EvalExpected expected) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ToolSeed(String toolName, String description, String riskLevel, boolean requiresHumanApproval,
                    List<String> allowedCategories, List<String> requiredFields, Integer maxAmountInr) {
    }
}