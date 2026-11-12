package com.trustdesk.ticket;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<TicketSummaryResponse> listTickets() {
        return ticketService.listTickets();
    }

    @GetMapping("/{ticketId}")
    public TicketDetailResponse getTicket(@PathVariable String ticketId) {
        return ticketService.getTicket(ticketId);
    }
}