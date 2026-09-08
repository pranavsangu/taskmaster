package com.taskmaster.controller;

import java.util.List;

import com.taskmaster.dto.request.ProjectRequest;
import com.taskmaster.dto.response.ProjectResponse;
import com.taskmaster.service.ProjectService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            Authentication authentication,
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(CREATED).body(projectService.create(authentication, request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(projectService.list(authentication));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> get(
            Authentication authentication,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.get(authentication, projectId));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(
            Authentication authentication,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.update(authentication, projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long projectId) {
        projectService.delete(authentication, projectId);
        return ResponseEntity.noContent().build();
    }
}
