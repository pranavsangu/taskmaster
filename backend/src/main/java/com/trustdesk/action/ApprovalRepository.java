package com.trustdesk.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, String> {

    List<Approval> findAllByActionActionIdOrderByCreatedAtAsc(String actionId);
}
