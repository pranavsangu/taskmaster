package com.trustdesk.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEvalLabelRepository extends JpaRepository<TicketEvalLabel, String> {
}