package com.taskmaster.controller;

import java.util.List;

import com.taskmaster.dto.request.AddMemberRequest;
import com.taskmaster.dto.response.ProjectMemberResponse;
import com.taskmaster.service.ProjectMembershipService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMembershipController {

    private final ProjectMembershipService projectMembershipService;

    public ProjectMembershipController(ProjectMembershipService projectMembershipService) {
        this.projectMembershipService = projectMembershipService;
    }

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> addMember(
            Authentication authentication,
            @PathVariable Long projectId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(CREATED)
                .body(projectMembershipService.addMember(authentication, projectId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(
            Authentication authentication,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectMembershipService.listMembers(authentication, projectId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        projectMembershipService.removeMember(authentication, projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
