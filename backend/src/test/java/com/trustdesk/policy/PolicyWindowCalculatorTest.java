package com.trustdesk.policy;

import com.trustdesk.customer.Customer;
import com.trustdesk.order.Order;
import com.trustdesk.order.OrderItem;
import com.trustdesk.policy.PolicyFacts.ReturnWindow;
import com.trustdesk.policy.PolicyFacts.Warranty;
import com.trustdesk.ticket.Ticket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: no Spring, no database. Edge cases of the return and warranty rules. */
class PolicyWindowCalculatorTest {

    private final PolicyWindowCalculator calculator = new PolicyWindowCalculator("Asia/Kolkata");

    private static final OrderItem EARBUDS = new OrderItem("BG-AIRPODS-01", "BlueBuds Air", 1, "audio", false);
    private static final OrderItem LICENSE = new OrderItem("BG-SOFT-01", "Cloud Backup", 1, "software", true);
    private static final OrderItem TABLET = new OrderItem("BG-TAB-10", "BlueTab 10", 1, "tablet", false);
    private static final OrderItem CASE = new OrderItem("BG-CASE-14", "Phone Case Pro", 1, "accessory", false);

    private static Ticket ticket(String createdAt, String tier, LocalDate deliveredAt, String total, OrderItem item) {
        Customer customer = new Customer("cus_1", "Test Customer", "t@example.com", tier, "IN",
                LocalDate.of(2024, 1, 1), true, List.of());
        Order order = item == null ? null : new Order("ord_1", customer, deliveredAt == null ? "in_transit" : "delivered",
                LocalDate.of(2025, 1, 1), deliveredAt, null, new BigDecimal(total), "INR", "paid", "TRK", List.of(item));
        return new Ticket("tkt_1", customer, order, "email", "s", "b", OffsetDateTime.parse(createdAt), "open");
    }

    @Test
    void returnWindowIncludesDaySevenAndClosesOnDayEight() {
        LocalDate delivered = LocalDate.of(2026, 6, 24);

        PolicyFacts day7 = calculator.calculate(ticket("2026-07-01T10:00:00+05:30", "standard", delivered, "8999", EARBUDS));
        PolicyFacts day8 = calculator.calculate(ticket("2026-07-02T10:00:00+05:30", "standard", delivered, "8999", EARBUDS));

        assertThat(day7.returnWindow()).isEqualTo(ReturnWindow.OPEN);
        assertThat(day7.returnWindowEndsOn()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(day7.returnEligible()).isTrue();
        assertThat(day8.returnWindow()).isEqualTo(ReturnWindow.CLOSED);
        assertThat(day8.daysSinceDelivery()).isEqualTo(8);
        assertThat(day8.returnEligible()).isFalse();
    }

    @Test
    void ticketDateIsTakenInBusinessTimeZoneNotUtc() {
        // 02:00 in India on 2 July is still 1 July in UTC. In India it is day 8: window closed.
        PolicyFacts facts = calculator.calculate(
                ticket("2026-07-02T02:00:00+05:30", "standard", LocalDate.of(2026, 6, 24), "8999", EARBUDS));

        assertThat(facts.asOfDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(facts.returnWindow()).isEqualTo(ReturnWindow.CLOSED);
    }

    @Test
    void softwareLicenseIsNeverReturnableAndHasNoWarranty() {
        PolicyFacts facts = calculator.calculate(
                ticket("2026-05-23T10:00:00+05:30", "gold", LocalDate.of(2026, 5, 22), "4999", LICENSE));

        assertThat(facts.returnWindow()).isEqualTo(ReturnWindow.OPEN); // window is open...
        assertThat(facts.returnEligible()).isFalse();                  // ...but the item is excluded
        assertThat(facts.items().get(0).notReturnableReason()).contains("software");
        assertThat(facts.items().get(0).warranty()).isEqualTo(Warranty.NOT_COVERED);
    }

    @Test
    void goldTierExtendsWarrantyBySixMonths() {
        LocalDate delivered = LocalDate.of(2025, 6, 5);
        String thirteenMonthsLater = "2026-07-01T15:30:00+05:30";

        PolicyFacts gold = calculator.calculate(ticket(thirteenMonthsLater, "gold", delivered, "34999", TABLET));
        PolicyFacts standard = calculator.calculate(ticket(thirteenMonthsLater, "standard", delivered, "34999", TABLET));

        assertThat(gold.items().get(0).warranty()).isEqualTo(Warranty.ACTIVE);
        assertThat(gold.items().get(0).goldExtensionApplied()).isTrue();
        assertThat(gold.items().get(0).warrantyEndsOn()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(standard.items().get(0).warranty()).isEqualTo(Warranty.EXPIRED);
        assertThat(standard.items().get(0).warrantyEndsOn()).isEqualTo(LocalDate.of(2026, 6, 5));
    }

    @Test
    void accessoryUnder3000GetsNoGoldExtension() {
        PolicyFacts facts = calculator.calculate(
                ticket("2026-07-01T10:00:00+05:30", "gold", LocalDate.of(2026, 6, 1), "2499", CASE));

        assertThat(facts.items().get(0).goldExtensionApplied()).isFalse();
        assertThat(facts.items().get(0).warrantyEndsOn()).isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void undeliveredOrderHasNoWindowsYet() {
        PolicyFacts facts = calculator.calculate(ticket("2026-07-02T12:20:00+05:30", "standard", null, "2499", CASE));

        assertThat(facts.returnWindow()).isEqualTo(ReturnWindow.NOT_DELIVERED);
        assertThat(facts.daysSinceDelivery()).isNull();
        assertThat(facts.items().get(0).warranty()).isEqualTo(Warranty.NOT_STARTED);
        assertThat(facts.returnEligible()).isFalse();
    }

    @Test
    void ticketWithoutOrder() {
        PolicyFacts facts = calculator.calculate(ticket("2026-07-02T12:20:00+05:30", "standard", null, "0", null));

        assertThat(facts.returnWindow()).isEqualTo(ReturnWindow.NO_ORDER);
        assertThat(facts.items()).isEmpty();
        assertThat(facts.returnEligible()).isFalse();
    }

    @Test
    void actionRules() {
        PolicyFacts license = calculator.calculate(
                ticket("2026-06-01T09:05:00+05:30", "standard", LocalDate.of(2026, 5, 22), "4999", LICENSE));
        PolicyFacts oldTabletGold = calculator.calculate(
                ticket("2026-07-01T15:30:00+05:30", "gold", LocalDate.of(2025, 6, 5), "34999", TABLET));

        assertThat(PolicyActionRules.blockReason("start_refund_review", "refund", license)).isPresent();
        assertThat(PolicyActionRules.blockReason("create_replacement_order", "refund", license)).isPresent();
        // Duplicate charges are not returns: billing refund reviews do not depend on the return window.
        assertThat(PolicyActionRules.blockReason("start_refund_review", "billing", license)).isEmpty();
        // Return window long closed, but warranty (with gold extension) still active.
        assertThat(PolicyActionRules.blockReason("create_replacement_order", "warranty", oldTabletGold)).isEmpty();
        assertThat(PolicyActionRules.blockReason("start_refund_review", "warranty", oldTabletGold)).isPresent();
        // Tools without a policy rule are left to the catalog check.
        assertThat(PolicyActionRules.blockReason("open_carrier_investigation", "shipping", license)).isEmpty();
    }
}
