package com.trustdesk.draft;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DraftReplyRepository extends JpaRepository<DraftReply, String> {

    /** The ticket's current draft: the most recent one. */
    Optional<DraftReply> findFirstByTicketTicketIdOrderByCreatedAtDesc(String ticketId);
}
