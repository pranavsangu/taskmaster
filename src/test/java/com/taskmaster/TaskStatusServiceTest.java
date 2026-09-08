package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.UpdateTaskStatusRequest;
import com.taskmaster.dto.response.TaskResponse;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.enums.TaskStatus;
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
class TaskStatusServiceTest {

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
    void completesAndReopensTaskThroughStatusFlow() {
        User user = user("user@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, user);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, user, ProjectRole.MEMBER)));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse completed = taskService.updateStatus(
                authentication, 5L, new UpdateTaskStatusRequest(TaskStatus.COMPLETED));
        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();

        TaskResponse reopened = taskService.updateStatus(
                authentication, 5L, new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS));
        assertThat(reopened.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reopened.completedAt()).isNull();
    }

    @Test
    void myTasksReturnsOnlyAssignedTasksInMemberProjects() {
        User user = user("user@example.com", 1L);
        Project memberProject = project(10L);
        Project inaccessibleProject = project(20L);
        Task visible = new Task("Visible", null, null, memberProject, user, user);
        Task hidden = new Task("Hidden", null, null, inaccessibleProject, user, user);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findByAssignedToIdOrderByDueDateAsc(1L))
                .thenReturn(List.of(visible, hidden));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(memberProject, user, ProjectRole.MEMBER)));
        when(projectMemberRepository.findByProjectIdAndUserId(20L, 1L))
                .thenReturn(Optional.empty());

        assertThat(taskService.myTasks(authentication))
                .extracting(TaskResponse::title)
                .containsExactly("Visible");
    }

    private static User user(String email, Long id) {
        User user = new User("User", email, "hash");
        setId(user, id);
        return user;
    }

    private static Project project(Long id) {
        Project project = new Project("Project", null);
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
