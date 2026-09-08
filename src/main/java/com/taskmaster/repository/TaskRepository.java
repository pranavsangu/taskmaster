package com.taskmaster.repository;

import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.taskmaster.domain.Task;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Task> {

    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Task> findByAssignedToIdOrderByDueDateAsc(Long userId);

    Page<Task> findAll(Specification<Task> specification, Pageable pageable);
}
