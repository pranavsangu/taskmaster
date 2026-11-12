package com.trustdesk.policy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Return and warranty facts for one ticket, calculated in Java as of the day the ticket was
 * created (business time zone), never as of today. Stored with each draft as jsonb.
 *
 * @param asOfDate           the ticket's creation date; every window is measured against it
 * @param returnWindowEndsOn last day a return can be requested (delivery + 7 days); null if not delivered
 * @param daysSinceDelivery  calendar days from delivery to {@code asOfDate}; null if not delivered
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicyFacts(
        LocalDate asOfDate,
        String orderId,
        String customerTier,
        ReturnWindow returnWindow,
        LocalDate returnWindowEndsOn,
        Integer daysSinceDelivery,
        List<ItemFacts> items) {

    public enum ReturnWindow { NO_ORDER, NOT_DELIVERED, OPEN, CLOSED }

    public enum Warranty { NO_ORDER, NOT_COVERED, NOT_STARTED, ACTIVE, EXPIRED }

    /**
     * @param notReturnableReason null when the item is returnable
     * @param warrantyNote        short explanation of the warranty result
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ItemFacts(
            String sku,
            String name,
            String category,
            boolean returnable,
            String notReturnableReason,
            Warranty warranty,
            LocalDate warrantyEndsOn,
            boolean goldExtensionApplied,
            String warrantyNote) {
    }

    /** A refund or return is supported by policy: window open and every item returnable. */
    public boolean returnEligible() {
        return returnWindow == ReturnWindow.OPEN && !items.isEmpty() && items.stream().allMatch(ItemFacts::returnable);
    }

    /** At least one item is under (possibly extended) warranty. */
    public boolean warrantyActive() {
        return items.stream().anyMatch(i -> i.warranty() == Warranty.ACTIVE);
    }

    /** Plain-language lines for the AI prompt and for reviewers. */
    public List<String> summaryLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Facts calculated as of the ticket date " + asOfDate + " (not today).");
        switch (returnWindow) {
            case NO_ORDER -> lines.add("No order is linked to this ticket.");
            case NOT_DELIVERED -> lines.add("Order " + orderId + " has not been delivered yet; no return window has started.");
            case OPEN -> lines.add("Return window: OPEN (delivered " + daysSinceDelivery + " days before the ticket; last day "
                    + returnWindowEndsOn + ").");
            case CLOSED -> lines.add("Return window: CLOSED (delivered " + daysSinceDelivery + " days before the ticket; ended "
                    + returnWindowEndsOn + ").");
        }
        for (ItemFacts item : items) {
            lines.add(item.name() + " (" + item.category() + "): "
                    + (item.returnable() ? "returnable" : "NOT returnable: " + item.notReturnableReason())
                    + "; warranty " + item.warranty() + " - " + item.warrantyNote() + ".");
        }
        lines.add(returnEligible() ? "A refund review or replacement is supported by the return policy."
                : "A refund review or return-based replacement is NOT supported by the return policy.");
        return lines;
    }
}
