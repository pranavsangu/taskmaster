package com.taskmaster.service;

import java.util.List;
import java.util.Locale;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.ProjectRequest;
import com.taskmaster.dto.response.ProjectResponse;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.exception.ForbiddenException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            AttachmentService attachmentService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public ProjectResponse create(Authentication authentication, ProjectRequest request) {
        User user = findCurrentUser(authentication);
        Project project = projectRepository.save(
                new Project(request.name().trim(), normalizeDescription(request.description())));
        ProjectMember ownerMembership = new ProjectMember(project, user, ProjectRole.OWNER);
        project.addMember(ownerMembership);
        projectMemberRepository.save(ownerMembership);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Authentication authentication) {
        User user = findCurrentUser(authentication);
        return projectMemberRepository.findByUserId(user.getId()).stream()
                .map(ProjectMember::getProject)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Authentication authentication, Long projectId) {
        return toResponse(requireMemberProject(authentication, projectId));
    }

    @Transactional
    public ProjectResponse update(Authentication authentication, Long projectId, ProjectRequest request) {
        Project project = requireOwnerProject(authentication, projectId);
        project.setName(request.name().trim());
        project.setDescription(normalizeDescription(request.description()));
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Authentication authentication, Long projectId) {
        Project project = requireOwnerProject(authentication, projectId);
        project.getTasks().forEach(task -> attachmentService.deleteFilesForTask(task.getId()));
        projectRepository.delete(project);
    }

    private Project requireMemberProject(Authentication authentication, Long projectId) {
        User user = findCurrentUser(authentication);
        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private Project requireOwnerProject(Authentication authentication, Long projectId) {
        User user = findCurrentUser(authentication);
        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (membership.getRole() != ProjectRole.OWNER) {
            throw new ForbiddenException("Only the project owner can perform this operation");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private User findCurrentUser(Authentication authentication) {
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
