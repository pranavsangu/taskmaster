package com.trustdesk.triage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TriageResultRepository extends JpaRepository<TriageResult, String> {

    /** The ticket's current triage: the most recent one. */
    Optional<TriageResult> findFirstByTicketTicketIdOrderByCreatedAtDesc(String ticketId);
}