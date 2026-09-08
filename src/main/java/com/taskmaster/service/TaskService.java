package com.taskmaster.service;

import java.util.List;
import java.util.Locale;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.AssignTaskRequest;
import com.taskmaster.dto.request.CreateTaskRequest;
import com.taskmaster.dto.request.UpdateTaskRequest;
import com.taskmaster.dto.request.UpdateTaskStatusRequest;
import com.taskmaster.dto.request.TaskQueryParams;
import com.taskmaster.dto.response.TaskResponse;
import com.taskmaster.dto.response.PageResponse;
import com.taskmaster.enums.TaskStatus;
import com.taskmaster.exception.BusinessRuleException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final AttachmentService attachmentService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            AttachmentService attachmentService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public TaskResponse create(Authentication authentication, Long projectId, CreateTaskRequest request) {
        User creator = findCurrentUser(authentication);
        requireProjectMember(projectId, creator.getId());
        Project project = findProject(projectId);
        User assignee = resolveAssignee(projectId, request.assignedToUserId());
        Task task = new Task(
                request.title().trim(),
                normalizeDescription(request.description()),
                request.dueDate(),
                project,
                creator,
                assignee);
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Authentication authentication, Long projectId) {
        User user = findCurrentUser(authentication);
        requireProjectMember(projectId, user.getId());
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Authentication authentication, Long taskId) {
        Task task = findTask(taskId);
        User user = findCurrentUser(authentication);
        requireProjectMember(task.getProject().getId(), user.getId());
        return toResponse(task);
    }

    @Transactional
    public TaskResponse update(
            Authentication authentication,
            Long taskId,
            UpdateTaskRequest request) {
        Task task = findTask(taskId);
        User user = findCurrentUser(authentication);
        requireProjectMember(task.getProject().getId(), user.getId());
        task.setTitle(request.title().trim());
        task.setDescription(normalizeDescription(request.description()));
        task.setDueDate(request.dueDate());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse assign(
            Authentication authentication,
            Long taskId,
            AssignTaskRequest request) {
        Task task = findTask(taskId);
        User user = findCurrentUser(authentication);
        Long projectId = task.getProject().getId();
        requireProjectMember(projectId, user.getId());
        task.setAssignedTo(resolveAssignee(projectId, request.userId()));
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(
            Authentication authentication,
            Long taskId,
            UpdateTaskStatusRequest request) {
        Task task = findTask(taskId);
        User user = findCurrentUser(authentication);
        requireProjectMember(task.getProject().getId(), user.getId());
        task.setStatus(request.status());
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> myTasks(Authentication authentication) {
        User user = findCurrentUser(authentication);
        return taskRepository.findByAssignedToIdOrderByDueDateAsc(user.getId()).stream()
                .filter(task -> projectMemberRepository
                        .findByProjectIdAndUserId(task.getProject().getId(), user.getId())
                        .isPresent())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> search(
            Authentication authentication,
            Long projectId,
            TaskQueryParams queryParams) {
        User user = findCurrentUser(authentication);
        requireProjectMember(projectId, user.getId());
        Specification<Task> specification = taskSpecification(projectId, queryParams, null);
        Page<Task> page = taskRepository.findAll(specification, pageable(queryParams));
        return toPageResponse(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> searchMyTasks(
            Authentication authentication,
            TaskQueryParams queryParams) {
        User user = findCurrentUser(authentication);
        Specification<Task> specification = taskSpecification(null, queryParams, user.getId());
        Page<Task> page = taskRepository.findAll(specification, pageable(queryParams));
        return toPageResponse(page);
    }

    @Transactional
    public void delete(Authentication authentication, Long taskId) {
        Task task = findTask(taskId);
        User user = findCurrentUser(authentication);
        requireProjectMember(task.getProject().getId(), user.getId());
        attachmentService.deleteFilesForTask(taskId);
        taskRepository.delete(task);
    }

    private User resolveAssignee(Long projectId, Long userId) {
        if (userId == null) {
            return null;
        }
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee user not found"));
        if (projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
            throw new BusinessRuleException("Assignee must be a member of the task project");
        }
        return assignee;
    }

    private void requireProjectMember(Long projectId, Long userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private User findCurrentUser(Authentication authentication) {
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private Specification<Task> taskSpecification(
            Long projectId,
            TaskQueryParams queryParams,
            Long assignedUserInMemberProject) {
        Specification<Task> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (projectId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("project").get("id"), projectId));
        }
        if (assignedUserInMemberProject != null) {
            specification = specification.and((root, query, cb) -> {
                query.distinct(true);
                return cb.and(
                        cb.equal(root.get("assignedTo").get("id"), assignedUserInMemberProject),
                        cb.equal(root.join("project").join("members").join("user").get("id"),
                                assignedUserInMemberProject));
            });
        } else if (queryParams.assignedTo() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("assignedTo").get("id"), queryParams.assignedTo()));
        }
        if (queryParams.status() != null && !queryParams.status().isBlank()) {
            TaskStatus status = parseStatus(queryParams.status());
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }
        if (queryParams.search() != null && !queryParams.search().isBlank()) {
            String pattern = "%" + queryParams.search().trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }
        return specification;
    }

    private Pageable pageable(TaskQueryParams queryParams) {
        if (queryParams.page() < 0 || queryParams.size() < 1 || queryParams.size() > 100) {
            throw new BusinessRuleException("page must be non-negative and size must be between 1 and 100");
        }
        if (queryParams.assignedTo() != null && queryParams.assignedTo() < 1) {
            throw new BusinessRuleException("assignedTo must be positive");
        }
        String sortValue = queryParams.sort();
        String field = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sortValue != null && !sortValue.isBlank()) {
            String[] parts = sortValue.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new BusinessRuleException("sort must use field,direction format");
            }
            field = parts[0].trim();
            if (parts.length == 2) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim());
                } catch (IllegalArgumentException exception) {
                    throw new BusinessRuleException("sort direction must be asc or desc");
                }
            }
        }
        if (!List.of("createdAt", "updatedAt", "dueDate", "status").contains(field)) {
            throw new BusinessRuleException("Unsupported task sort field");
        }
        return PageRequest.of(queryParams.page(), queryParams.size(), Sort.by(direction, field));
    }

    private TaskStatus parseStatus(String status) {
        try {
            return TaskStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Unsupported task status");
        }
    }

    private PageResponse<TaskResponse> toPageResponse(Page<Task> page) {
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.getStatus(),
                task.getProject().getId(),
                task.getCreatedBy().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt()
        );
    }
}
