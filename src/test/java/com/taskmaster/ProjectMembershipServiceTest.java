package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.lang.reflect.Field;

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
import com.taskmaster.service.ProjectMembershipService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ProjectMembershipServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProjectMembershipService membershipService;

    @Test
    void ownerAddsRegisteredUserAsMember() {
        User owner = new User("Owner", "owner@example.com", "hash");
        User member = new User("Member", "member@example.com", "hash");
        setId(owner, 1L);
        setId(member, 2L);
        Project project = new Project("Taskmaster", null);
        ProjectMember ownerMembership = new ProjectMember(project, owner, ProjectRole.OWNER);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, owner.getId()))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, member.getId()))
                .thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectMemberResponse response = membershipService.addMember(
                authentication, 1L, new AddMemberRequest(" MEMBER@EXAMPLE.COM "));

        ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.email()).isEqualTo("member@example.com");
    }

    @Test
    void rejectsDuplicateMembership() {
        User owner = new User("Owner", "owner@example.com", "hash");
        User member = new User("Member", "member@example.com", "hash");
        setId(owner, 1L);
        setId(member, 2L);
        Project project = new Project("Taskmaster", null);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, owner.getId()))
                .thenReturn(Optional.of(new ProjectMember(project, owner, ProjectRole.OWNER)));
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, member.getId()))
                .thenReturn(Optional.of(new ProjectMember(project, member, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> membershipService.addMember(
                authentication, 1L, new AddMemberRequest("member@example.com")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
    }

    @Test
    void memberCannotManageMembership() {
        User member = new User("Member", "member@example.com", "hash");
        setId(member, 2L);
        Project project = new Project("Taskmaster", null);
        when(authentication.getName()).thenReturn("member@example.com");
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, member.getId()))
                .thenReturn(Optional.of(new ProjectMember(project, member, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> membershipService.addMember(
                authentication, 1L, new AddMemberRequest("other@example.com")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void preventsRemovingOnlyOwner() {
        User owner = new User("Owner", "owner@example.com", "hash");
        setId(owner, 1L);
        Project project = new Project("Taskmaster", null);
        ProjectMember ownerMembership = new ProjectMember(project, owner, ProjectRole.OWNER);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, owner.getId()))
                .thenReturn(Optional.of(ownerMembership));
        when(projectMemberRepository.countByProjectIdAndRole(1L, ProjectRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> membershipService.removeMember(authentication, 1L, owner.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(projectMemberRepository, never()).delete(any(ProjectMember.class));
    }

    @Test
    void listsMembersForAnyProjectMember() {
        User member = new User("Member", "member@example.com", "hash");
        setId(member, 2L);
        Project project = new Project("Taskmaster", null);
        ProjectMember membership = new ProjectMember(project, member, ProjectRole.MEMBER);
        when(authentication.getName()).thenReturn("member@example.com");
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, member.getId()))
                .thenReturn(Optional.of(membership));
        when(projectMemberRepository.findByProjectId(1L)).thenReturn(List.of(membership));

        assertThat(membershipService.listMembers(authentication, 1L)).hasSize(1);
    }

    @Test
    void unknownTargetUserReturnsNotFound() {
        User owner = new User("Owner", "owner@example.com", "hash");
        setId(owner, 1L);
        Project project = new Project("Taskmaster", null);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, owner.getId()))
                .thenReturn(Optional.of(new ProjectMember(project, owner, ProjectRole.OWNER)));
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.addMember(
                authentication, 1L, new AddMemberRequest("missing@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static void setId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
