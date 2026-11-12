package com.trustdesk.policy;

import com.trustdesk.order.Order;
import com.trustdesk.order.OrderItem;
import com.trustdesk.policy.PolicyFacts.ItemFacts;
import com.trustdesk.policy.PolicyFacts.ReturnWindow;
import com.trustdesk.policy.PolicyFacts.Warranty;
import com.trustdesk.ticket.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Calculates return and warranty windows from KB-REFUND-001 and KB-WARRANTY-001.
 * <p>
 * Every window is measured from the ticket's {@code created_at}, converted to the business time
 * zone (Asia/Kolkata), never from today's date: the seed data is static, and the answer must not
 * change depending on when the app runs.
 * <ul>
 *   <li>Return: within 7 calendar days of delivery (last day = delivery + 7).</li>
 *   <li>Not returnable: software licenses, downloadable products, gift cards, final-sale items.</li>
 *   <li>Warranty: 12 months from delivery; gold tier +6 months on eligible hardware
 *       (not software, not final sale, not accessories under INR 3000). Coverage includes the end date.</li>
 * </ul>
 */
@Component
public class PolicyWindowCalculator {

    static final int RETURN_DAYS = 7;
    static final int WARRANTY_MONTHS = 12;
    static final int GOLD_EXTENSION_MONTHS = 6;
    static final BigDecimal ACCESSORY_EXTENSION_MIN_INR = new BigDecimal("3000");
    static final Set<String> NON_RETURNABLE_CATEGORIES = Set.of("software", "digital", "download", "gift_card");

    private final ZoneId businessZone;

    public PolicyWindowCalculator(@Value("${trustdesk.business-time-zone}") String businessZone) {
        this.businessZone = ZoneId.of(businessZone);
    }

    public PolicyFacts calculate(Ticket ticket) {
        LocalDate asOf = ticket.getCreatedAt().atZoneSameInstant(businessZone).toLocalDate();
        String tier = ticket.getCustomer().getTier();
        Order order = ticket.getOrder();
        if (order == null) {
            return new PolicyFacts(asOf, null, tier, ReturnWindow.NO_ORDER, null, null, List.of());
        }

        LocalDate delivered = order.getDeliveredAt();
        ReturnWindow window;
        LocalDate windowEnds = null;
        Integer daysSinceDelivery = null;
        if (delivered == null) {
            window = ReturnWindow.NOT_DELIVERED;
        } else {
            windowEnds = delivered.plusDays(RETURN_DAYS);
            daysSinceDelivery = (int) ChronoUnit.DAYS.between(delivered, asOf);
            window = asOf.isAfter(windowEnds) ? ReturnWindow.CLOSED : ReturnWindow.OPEN;
        }

        List<ItemFacts> items = order.getItems().stream()
                .map(item -> itemFacts(item, asOf, delivered, tier, order.getTotal(), order.getItems().size()))
                .toList();
        return new PolicyFacts(asOf, order.getOrderId(), tier, window, windowEnds, daysSinceDelivery, items);
    }

    private static ItemFacts itemFacts(OrderItem item, LocalDate asOf, LocalDate delivered, String tier,
                                       BigDecimal orderTotal, int itemCount) {
        String category = item.category() == null ? "" : item.category().toLowerCase(Locale.ROOT);
        boolean software = NON_RETURNABLE_CATEGORIES.contains(category);

        String notReturnable = null;
        if (software) {
            notReturnable = "software licenses and digital products are not eligible for refund after purchase";
        } else if (item.finalSale()) {
            notReturnable = "final-sale item";
        }

        if (software) {
            return new ItemFacts(item.sku(), item.name(), category, false, notReturnable,
                    Warranty.NOT_COVERED, null, false, "warranty does not cover software");
        }
        if (delivered == null) {
            return new ItemFacts(item.sku(), item.name(), category, notReturnable == null, notReturnable,
                    Warranty.NOT_STARTED, null, false, "warranty starts on delivery");
        }

        boolean extension = "gold".equalsIgnoreCase(tier) && !item.finalSale()
                && !isCheapAccessory(category, orderTotal, itemCount);
        LocalDate ends = delivered.plusMonths(WARRANTY_MONTHS + (extension ? GOLD_EXTENSION_MONTHS : 0));
        boolean active = !asOf.isAfter(ends);
        String note = (extension ? "12 months + 6-month gold extension" : "12 months")
                + (active ? ", covered until " : ", expired on ") + ends;
        return new ItemFacts(item.sku(), item.name(), category, notReturnable == null, notReturnable,
                active ? Warranty.ACTIVE : Warranty.EXPIRED, ends, extension, note);
    }

    /**
     * Items have no price of their own, so an accessory's price is only known when it is the
     * order's only item (price = order total). Unknown price: no extension (the safe choice).
     */
    private static boolean isCheapAccessory(String category, BigDecimal orderTotal, int itemCount) {
        if (!"accessory".equals(category)) {
            return false;
        }
        return itemCount != 1 || orderTotal == null || orderTotal.compareTo(ACCESSORY_EXTENSION_MIN_INR) < 0;
    }
}