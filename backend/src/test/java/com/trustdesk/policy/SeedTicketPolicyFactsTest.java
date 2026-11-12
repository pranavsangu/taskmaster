package com.trustdesk.policy;

import com.trustdesk.policy.PolicyFacts.ReturnWindow;
import com.trustdesk.policy.PolicyFacts.Warranty;
import com.trustdesk.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window outcome for every seed ticket, loaded from the database. These results must stay
 * the same no matter what today's date is.
 */
@SpringBootTest
class SeedTicketPolicyFactsTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PolicyWindowCalculator calculator;

    private PolicyFacts facts(String ticketId) {
        return calculator.calculate(ticketRepository.findWithContextByTicketId(ticketId).orElseThrow());
    }

    @Test
    void tkt9001DamagedEarbudsIsWithinReturnWindow() {
        PolicyFacts f = facts("tkt_9001");
        assertThat(f.asOfDate()).isEqualTo(LocalDate.of(2026, 6, 28));
        assertThat(f.daysSinceDelivery()).isEqualTo(4);
        assertThat(f.returnWindow()).isEqualTo(ReturnWindow.OPEN);
        assertThat(f.returnEligible()).isTrue();
    }

    @Test
    void tkt9002PackageNotDeliveredYet() {
        assertThat(facts("tkt_9002").returnWindow()).isEqualTo(ReturnWindow.NOT_DELIVERED);
    }

    @Test
    void tkt9003SoftwareLicenseIsNotRefundable() {
        PolicyFacts f = facts("tkt_9003");
        assertThat(f.returnWindow()).isEqualTo(ReturnWindow.CLOSED); // delivered 22 May, ticket 1 June
        assertThat(f.returnEligible()).isFalse();
        assertThat(f.items().get(0).returnable()).isFalse();
        assertThat(PolicyActionRules.blockReason("start_refund_review", "refund", f)).isPresent();
    }

    @Test
    void tkt9004GoldTabletIsUnderExtendedWarrantyAfter13Months() {
        PolicyFacts f = facts("tkt_9004");
        assertThat(f.returnWindow()).isEqualTo(ReturnWindow.CLOSED);
        assertThat(f.items().get(0).warranty()).isEqualTo(Warranty.ACTIVE);
        assertThat(f.items().get(0).goldExtensionApplied()).isTrue();
        assertThat(f.items().get(0).warrantyEndsOn()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void tkt9008DoubleChargeOrderDeliveredTwoDaysBefore() {
        PolicyFacts f = facts("tkt_9008");
        assertThat(f.daysSinceDelivery()).isEqualTo(2);
        assertThat(f.returnWindow()).isEqualTo(ReturnWindow.OPEN);
    }
}