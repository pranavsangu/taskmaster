package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.AssignTaskRequest;
import com.taskmaster.dto.request.CreateTaskRequest;
import com.taskmaster.dto.request.UpdateTaskRequest;
import com.taskmaster.dto.response.TaskResponse;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.enums.TaskStatus;
import com.taskmaster.exception.BusinessRuleException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.service.TaskService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createsTodoTaskForProjectMember() {
        User creator = user("creator@example.com", 1L);
        Project project = project(10L);
        when(authentication.getName()).thenReturn("creator@example.com");
        when(userRepository.findByEmail("creator@example.com")).thenReturn(Optional.of(creator));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, creator, ProjectRole.OWNER)));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(
                authentication,
                10L,
                new CreateTaskRequest(" Task ", " Details ", LocalDate.of(2026, 9, 15), null));

        assertThat(response.title()).isEqualTo("Task");
        assertThat(response.description()).isEqualTo("Details");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.assignedToUserId()).isNull();
    }

    @Test
    void updatesTaskDetailsWithoutChangingStatusOrAssignee() {
        User creator = user("creator@example.com", 1L);
        User assignee = user("assignee@example.com", 2L);
        Project project = project(10L);
        Task task = new Task("Old", "Old details", null, project, creator, assignee);
        task.setStatus(TaskStatus.IN_PROGRESS);
        when(authentication.getName()).thenReturn("creator@example.com");
        when(userRepository.findByEmail("creator@example.com")).thenReturn(Optional.of(creator));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, creator, ProjectRole.MEMBER)));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskService.update(authentication, 5L, new UpdateTaskRequest("New", "New details", null));

        assertThat(task.getTitle()).isEqualTo("New");
        assertThat(task.getDescription()).isEqualTo("New details");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getAssignedTo()).isSameAs(assignee);
    }

    @Test
    void rejectsAssigneeFromAnotherProject() {
        User creator = user("creator@example.com", 1L);
        User assignee = user("assignee@example.com", 2L);
        Project project = project(10L);
        when(authentication.getName()).thenReturn("creator@example.com");
        when(userRepository.findByEmail("creator@example.com")).thenReturn(Optional.of(creator));
        when(taskRepository.findById(5L))
                .thenReturn(Optional.of(new Task("Task", null, null, project, creator, null)));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, creator, ProjectRole.MEMBER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.assign(
                authentication, 5L, new AssignTaskRequest(2L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void returnsNotFoundForMissingAssignee() {
        User creator = user("creator@example.com", 1L);
        Project project = project(10L);
        when(authentication.getName()).thenReturn("creator@example.com");
        when(userRepository.findByEmail("creator@example.com")).thenReturn(Optional.of(creator));
        when(taskRepository.findById(5L))
                .thenReturn(Optional.of(new Task("Task", null, null, project, creator, null)));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, creator, ProjectRole.MEMBER)));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.assign(
                authentication, 5L, new AssignTaskRequest(99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unassignsTaskForProjectMember() {
        User creator = user("creator@example.com", 1L);
        User assignee = user("assignee@example.com", 2L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, creator, assignee);
        when(authentication.getName()).thenReturn("creator@example.com");
        when(userRepository.findByEmail("creator@example.com")).thenReturn(Optional.of(creator));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, creator, ProjectRole.MEMBER)));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskService.assign(authentication, 5L, new AssignTaskRequest(null));

        assertThat(task.getAssignedTo()).isNull();
    }

    @Test
    void nonMemberCannotDeleteTask() {
        User user = user("other@example.com", 3L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, null);
        when(authentication.getName()).thenReturn("other@example.com");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(authentication, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void nonMemberCannotReadTask() {
        User user = user("other@example.com", 3L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, null);
        when(authentication.getName()).thenReturn("other@example.com");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.get(authentication, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static User user(String email, Long id) {
        User user = new User(email, email, "hash");
        setId(user, id);
        return user;
    }

    private static Project project(Long id) {
        Project project = new Project("Taskmaster", null);
        setId(project, id);
        return project;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
