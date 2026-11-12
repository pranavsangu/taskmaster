package com.trustdesk.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    /** All tickets with their customer loaded in the same query (avoids one extra query per ticket). */
    @EntityGraph(attributePaths = "customer")
    List<Ticket> findAllByOrderByTicketIdAsc();

    /** One ticket with its customer and order loaded in the same query. */
    @EntityGraph(attributePaths = {"customer", "order"})
    Optional<Ticket> findWithContextByTicketId(String ticketId);
}