package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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
import com.taskmaster.service.ProjectService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createsProjectAndOwnerMembership() {
        User user = new User("John Doe", "john@example.com", "hash");
        Project project = new Project("Taskmaster", "Delivery project");
        when(authentication.getName()).thenReturn("john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.create(
                authentication,
                new ProjectRequest(" Taskmaster ", " Delivery project "));

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getProject()).isSameAs(project);
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(response.name()).isEqualTo("Taskmaster");
        assertThat(response.description()).isEqualTo("Delivery project");
    }

    @Test
    void listsOnlyProjectsForCurrentUser() {
        User user = new User("John Doe", "john@example.com", "hash");
        Project project = new Project("Taskmaster", null);
        when(authentication.getName()).thenReturn("john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByUserId(user.getId()))
                .thenReturn(List.of(new ProjectMember(project, user, ProjectRole.OWNER)));

        List<ProjectResponse> response = projectService.list(authentication);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).name()).isEqualTo("Taskmaster");
    }

    @Test
    void memberCanReadButCannotUpdateProject() {
        User user = new User("Member", "member@example.com", "hash");
        Project project = new Project("Taskmaster", null);
        ProjectMember membership = new ProjectMember(project, user, ProjectRole.MEMBER);
        when(authentication.getName()).thenReturn("member@example.com");
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, user.getId()))
                .thenReturn(Optional.of(membership));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThat(projectService.get(authentication, 1L).name()).isEqualTo("Taskmaster");
        assertThatThrownBy(() -> projectService.update(
                authentication, 1L, new ProjectRequest("Changed", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void nonMemberCannotReadProject() {
        User user = new User("Other", "other@example.com", "hash");
        when(authentication.getName()).thenReturn("other@example.com");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(user));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.get(authentication, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
