package com.trustdesk.ticket;

import com.trustdesk.common.ResourceNotFoundException;
import com.trustdesk.customer.Customer;
import com.trustdesk.order.Order;
import com.trustdesk.ticket.TicketDetailResponse.CustomerContext;
import com.trustdesk.ticket.TicketDetailResponse.OrderContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Reads tickets and turns them into API responses. Entities never leave this class,
 * so evaluation labels and lazy-loading surprises cannot leak into the JSON.
 */
@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ZoneId businessZone;

    public TicketService(TicketRepository ticketRepository,
                         @Value("${trustdesk.business-time-zone}") String businessZone) {
        this.ticketRepository = ticketRepository;
        this.businessZone = ZoneId.of(businessZone);
    }

    public List<TicketSummaryResponse> listTickets() {
        return ticketRepository.findAllByOrderByTicketIdAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    public TicketDetailResponse getTicket(String ticketId) {
        Ticket ticket = ticketRepository.findWithContextByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
        return toDetail(ticket);
    }

    private TicketSummaryResponse toSummary(Ticket ticket) {
        Order order = ticket.getOrder();
        return new TicketSummaryResponse(
                ticket.getTicketId(),
                ticket.getSubject(),
                ticket.getChannel(),
                ticket.getStatus(),
                inBusinessZone(ticket.getCreatedAt()),
                ticket.getCustomer().getCustomerId(),
                ticket.getCustomer().getName(),
                order == null ? null : order.getOrderId());
    }

    private TicketDetailResponse toDetail(Ticket ticket) {
        return new TicketDetailResponse(
                ticket.getTicketId(),
                ticket.getChannel(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus(),
                inBusinessZone(ticket.getCreatedAt()),
                toCustomerContext(ticket.getCustomer()),
                ticket.getOrder() == null ? null : toOrderContext(ticket.getOrder()));
    }

    private static CustomerContext toCustomerContext(Customer customer) {
        return new CustomerContext(
                customer.getCustomerId(),
                customer.getName(),
                customer.getEmail(),
                customer.getTier(),
                customer.getCountry(),
                customer.isVerified(),
                customer.getTags(),
                customer.getCreatedAt());
    }

    private static OrderContext toOrderContext(Order order) {
        return new OrderContext(
                order.getOrderId(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getDeliveredAt(),
                order.getEligibleReturnUntil(),
                order.getTotal(),
                order.getCurrency(),
                order.getPaymentStatus(),
                order.getTrackingNumber(),
                order.getItems());
    }

    /**
     * The database stores the exact moment but not the original offset, so it comes back in
     * the server's zone. Showing it in the business zone restores e.g. 2026-06-28T10:15:00+05:30.
     */
    private OffsetDateTime inBusinessZone(OffsetDateTime moment) {
        return moment.atZoneSameInstant(businessZone).toOffsetDateTime();
    }
}