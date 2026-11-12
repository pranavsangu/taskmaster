package com.trustdesk.action;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;

public interface ToolActionRequestRepository extends JpaRepository<ToolActionRequest, String> {

    /** Loads the ticket too: used outside a transaction when replaying an idempotent request. */
    @EntityGraph(attributePaths = "ticket")
    Optional<ToolActionRequest> findByIdempotencyKey(String idempotencyKey);

    List<ToolActionRequest> findAllByTicketTicketIdOrderByCreatedAtAsc(String ticketId);

    /**
     * Atomically moves an action from {@code approved} to {@code executing}. PostgreSQL locks the row,
     * so of several simultaneous calls exactly one changes it (returns 1); the others see that it is no
     * longer {@code approved} (return 0). Only the caller that got 1 may run the tool.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ToolActionRequest a
               set a.status = 'executing', a.updatedAt = :now, a.version = a.version + 1
             where a.actionId = :actionId and a.status = 'approved'
            """)
    int claimForExecution(@Param("actionId") String actionId, @Param("now") OffsetDateTime now);
}
