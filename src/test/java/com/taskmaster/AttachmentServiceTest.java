package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.taskmaster.domain.Project;
import com.taskmaster.domain.ProjectMember;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.domain.Attachment;
import com.taskmaster.exception.BusinessRuleException;
import com.taskmaster.repository.AttachmentRepository;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.service.AttachmentService;
import com.taskmaster.enums.ProjectRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private Authentication authentication;
    @TempDir Path tempDir;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(attachmentRepository, projectMemberRepository,
                taskRepository, userRepository, tempDir.toString(), 10);
    }

    @Test
    void uploadsWithSafeGeneratedNameAndMetadata() throws Exception {
        User user = user("user@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, null);
        setId(task, 5L);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, user, ProjectRole.MEMBER)));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = attachmentService.upload(authentication, 5L,
                new MockMultipartFile("file", "../requirements.pdf", "APPLICATION/PDF", "hello".getBytes()));

        assertThat(response.originalFileName()).isEqualTo("requirements.pdf");
        assertThat(response.fileType()).isEqualTo("application/pdf");
        assertThat(Files.list(tempDir).count()).isEqualTo(1);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        User user = user("user@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, null);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new ProjectMember(project, user, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> attachmentService.upload(authentication, 5L,
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> attachmentService.upload(authentication, 5L,
                new MockMultipartFile("file", "large.txt", "text/plain", "12345678901".getBytes())))
                .isInstanceOf(BusinessRuleException.class);
        verify(attachmentRepository, never()).save(any(Attachment.class));
    }

    @Test
    void nonMemberCannotUpload() {
        User user = user("user@example.com", 1L);
        Project project = project(10L);
        Task task = new Task("Task", null, null, project, user, null);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectMemberRepository.findByProjectIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.upload(authentication, 5L,
                new MockMultipartFile("file", "file.txt", "text/plain", "hello".getBytes())))
                .isInstanceOf(com.taskmaster.exception.ResourceNotFoundException.class);
    }

    private static User user(String email, Long id) { User user = new User("User", email, "hash"); setId(user, id); return user; }
    private static Project project(Long id) { Project project = new Project("Project", null); setId(project, id); return project; }
    private static void setId(Object entity, Long id) {
        try { Field field = entity.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(entity, id); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
