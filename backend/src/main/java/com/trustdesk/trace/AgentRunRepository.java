package com.trustdesk.trace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {

    List<AgentRun> findAllByTicketTicketIdOrderByCreatedAtAsc(String ticketId);
}
