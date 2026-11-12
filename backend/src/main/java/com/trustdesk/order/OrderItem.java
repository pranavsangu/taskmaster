package com.trustdesk.order;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One line item inside an order. Stored as part of the order's {@code items} jsonb column,
 * using the same snake_case field names as orders.json.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderItem(String sku, String name, int quantity, String category, boolean finalSale) {
}