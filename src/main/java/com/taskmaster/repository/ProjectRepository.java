package com.taskmaster.repository;

import com.taskmaster.domain.Project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
