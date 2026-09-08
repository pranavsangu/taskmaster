package com.taskmaster.repository;

import java.util.List;
import java.util.Optional;

import com.taskmaster.domain.ProjectMember;
import com.taskmaster.enums.ProjectRole;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectMember> findByUserId(Long userId);

    List<ProjectMember> findByProjectId(Long projectId);

    long countByProjectIdAndRole(Long projectId, ProjectRole role);
}
