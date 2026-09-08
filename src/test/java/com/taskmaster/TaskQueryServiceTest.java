package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.TaskQueryParams;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.exception.BusinessRuleException;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.service.TaskService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class TaskQueryServiceTest {

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
    void returnsPagedProjectTasksWithRequestedSort() {
        User user = user("user@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Authentication", "JWT search", null, project, user, null);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, user, ProjectRole.MEMBER)));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task), Pageable.ofSize(5), 1));

        var response = taskService.search(
                authentication,
                10L,
                new TaskQueryParams("TODO", null, "jwt", 1, 5, "dueDate,asc"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(pageable.getValue().getSort().getOrderFor("dueDate").isAscending()).isTrue();
    }

    @Test
    void rejectsUnsupportedStatusFilter() {
        User user = user("user@example.com", 1L);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project(10L), user, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> taskService.search(
                authentication,
                10L,
                new TaskQueryParams("UNKNOWN", null, null, 0, 20, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsUnsupportedSortField() {
        User user = user("user@example.com", 1L);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project(10L), user, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> taskService.search(
                authentication,
                10L,
                new TaskQueryParams(null, null, null, 0, 20, "priority,asc")))
                .isInstanceOf(BusinessRuleException.class);
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
