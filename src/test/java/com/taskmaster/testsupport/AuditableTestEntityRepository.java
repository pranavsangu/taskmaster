package com.taskmaster.testsupport;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditableTestEntityRepository extends JpaRepository<AuditableTestEntity, Long> {
}
