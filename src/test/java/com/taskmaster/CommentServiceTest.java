package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.taskmaster.domain.Comment;
import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.CreateCommentRequest;
import com.taskmaster.dto.request.UpdateCommentRequest;
import com.taskmaster.exception.ForbiddenException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.repository.CommentRepository;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.service.CommentService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private Authentication authentication;
    @InjectMocks private CommentService commentService;

    @Test
    void memberCanCreateAndListComments() {
        User author = user("author@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, author, null);
        setId(task, 5L);
        when(authentication.getName()).thenReturn("author@example.com");
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, author, ProjectRole.MEMBER)));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = commentService.create(authentication, 5L, new CreateCommentRequest("  hello  "));
        assertThat(created.content()).isEqualTo("hello");

        when(commentRepository.findByTaskIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());
        assertThat(commentService.list(authentication, 5L)).isEmpty();
    }

    @Test
    void nonMemberCannotAccessComments() {
        User user = user("other@example.com", 2L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, null);
        when(authentication.getName()).thenReturn("other@example.com");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.list(authentication, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authorCanUpdateOwnComment() {
        User author = user("author@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, author, null);
        Comment comment = new Comment(task, author, "old");
        when(authentication.getName()).thenReturn("author@example.com");
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(comment));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, author, ProjectRole.MEMBER)));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commentService.update(authentication, 7L, new UpdateCommentRequest("new"));
        assertThat(comment.getContent()).isEqualTo("new");
    }

    @Test
    void nonAuthorCannotUpdateOrDeleteComment() {
        User author = user("author@example.com", 1L);
        User member = user("member@example.com", 2L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, author, null);
        Comment comment = new Comment(task, author, "old");
        when(authentication.getName()).thenReturn("member@example.com");
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(comment));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(new ProjectMember(project, member, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> commentService.update(authentication, 7L, new UpdateCommentRequest("new")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> commentService.delete(authentication, 7L))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void authorCanDeleteOwnComment() {
        User author = user("author@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, author, null);
        Comment comment = new Comment(task, author, "old");
        when(authentication.getName()).thenReturn("author@example.com");
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(comment));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, author, ProjectRole.MEMBER)));

        commentService.delete(authentication, 7L);
        verify(commentRepository).delete(comment);
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
