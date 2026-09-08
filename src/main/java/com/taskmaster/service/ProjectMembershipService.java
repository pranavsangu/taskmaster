package com.taskmaster.service;

import java.util.List;
import java.util.Locale;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.AddMemberRequest;
import com.taskmaster.dto.response.ProjectMemberResponse;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.exception.DuplicateResourceException;
import com.taskmaster.exception.ForbiddenException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMembershipService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectMembershipService(
            ProjectMemberRepository projectMemberRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectMemberResponse addMember(
            Authentication authentication,
            Long projectId,
            AddMemberRequest request) {
        requireOwner(authentication, projectId);
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId()).isPresent()) {
            throw new DuplicateResourceException("User is already a project member");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        ProjectMember membership = new ProjectMember(project, user, ProjectRole.MEMBER);
        project.addMember(membership);
        return toResponse(projectMemberRepository.save(membership));
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(Authentication authentication, Long projectId) {
        requireMembership(authentication, projectId);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeMember(Authentication authentication, Long projectId, Long userId) {
        requireOwner(authentication, projectId);
        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project member not found"));

        if (membership.getRole() == ProjectRole.OWNER
                && projectMemberRepository.countByProjectIdAndRole(projectId, ProjectRole.OWNER) <= 1) {
            throw new ForbiddenException("Cannot remove the only project owner");
        }

        projectMemberRepository.delete(membership);
    }

    private ProjectMember requireOwner(Authentication authentication, Long projectId) {
        ProjectMember membership = requireMembership(authentication, projectId);
        if (membership.getRole() != ProjectRole.OWNER) {
            throw new ForbiddenException("Only the project owner can manage membership");
        }
        return membership;
    }

    private ProjectMember requireMembership(Authentication authentication, Long projectId) {
        User currentUser = userRepository.findByEmail(normalizeEmail(authentication.getName()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return projectMemberRepository.findByProjectIdAndUserId(projectId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ProjectMemberResponse toResponse(ProjectMember membership) {
        User user = membership.getUser();
        return new ProjectMemberResponse(
                membership.getId(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                membership.getRole(),
                membership.getJoinedAt()
        );
    }
}
