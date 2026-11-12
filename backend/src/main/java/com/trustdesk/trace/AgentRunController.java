package com.trustdesk.trace;

import com.trustdesk.common.ResourceNotFoundException;
import com.trustdesk.ticket.TicketRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgentRunController {

    private final AgentRunRepository agentRunRepository;
    private final TicketRepository ticketRepository;

    public AgentRunController(AgentRunRepository agentRunRepository, TicketRepository ticketRepository) {
        this.agentRunRepository = agentRunRepository;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/agent-runs/{runId}")
    @Transactional(readOnly = true)
    public AgentRunResponse get(@PathVariable String runId) {
        return agentRunRepository.findById(runId)
                .map(AgentRunResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Agent run " + runId + " not found"));
    }

    /** All runs for a ticket, oldest first: the ticket's AI history. */
    @GetMapping("/tickets/{ticketId}/agent-runs")
    @Transactional(readOnly = true)
    public List<AgentRunResponse> forTicket(@PathVariable String ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " not found");
        }
        return agentRunRepository.findAllByTicketTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(AgentRunResponse::from)
                .toList();
    }
}
