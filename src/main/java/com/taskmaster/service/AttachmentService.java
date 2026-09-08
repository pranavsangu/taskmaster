package com.taskmaster.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.taskmaster.domain.Attachment;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.response.AttachmentResponse;
import com.taskmaster.exception.BusinessRuleException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.AttachmentRepository;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Path storageDirectory;
    private final long maxSize;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            ProjectMemberRepository projectMemberRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            @Value("${app.attachments.storage-dir:uploads}") String storageDirectory,
            @Value("${app.attachments.max-size:10485760}") long maxSize) {
        this.attachmentRepository = attachmentRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.storageDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    @Transactional
    public AttachmentResponse upload(Authentication authentication, Long taskId, MultipartFile file) {
        User user = findCurrentUser(authentication);
        Task task = findTask(taskId);
        requireMember(task, user);
        validateFile(file);
        try {
            Files.createDirectories(storageDirectory);
            String storedName = UUID.randomUUID() + extension(file.getOriginalFilename());
            Path target = storageDirectory.resolve(storedName).normalize();
            if (!target.getParent().equals(storageDirectory)) {
                throw new BusinessRuleException("Unsafe attachment path");
            }
            Files.copy(file.getInputStream(), target);
            Attachment attachment = new Attachment(
                    task,
                    storedName,
                    sanitizeOriginalName(file.getOriginalFilename()),
                    sanitizeContentType(file.getContentType()),
                    file.getSize(),
                    target.toString(),
                    user);
            return toResponse(attachmentRepository.save(attachment));
        } catch (IOException exception) {
            throw new BusinessRuleException("Attachment could not be stored");
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Authentication authentication, Long taskId) {
        User user = findCurrentUser(authentication);
        Task task = findTask(taskId);
        requireMember(task, user);
        return attachmentRepository.findByTaskIdOrderByUploadedAtAsc(taskId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(Authentication authentication, Long attachmentId) {
        User user = findCurrentUser(authentication);
        Attachment attachment = findAttachment(attachmentId);
        requireMember(attachment.getTask(), user);
        try {
            Path path = safeStoredPath(attachment.getStoragePath());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Attachment file not found");
            }
            MediaType mediaType = MediaType.parseMediaType(attachment.getFileType());
            return ResponseEntity.ok().contentType(mediaType)
                    .header("Content-Disposition", "attachment; filename=\""
                            + sanitizeHeaderFilename(attachment.getOriginalFileName()) + "\"")
                    .body(resource);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Attachment file not found");
        }
    }

    @Transactional
    public void delete(Authentication authentication, Long attachmentId) {
        User user = findCurrentUser(authentication);
        Attachment attachment = findAttachment(attachmentId);
        requireMember(attachment.getTask(), user);
        try {
            Files.deleteIfExists(safeStoredPath(attachment.getStoragePath()));
        } catch (IOException exception) {
            throw new BusinessRuleException("Attachment file could not be deleted");
        }
        attachmentRepository.delete(attachment);
    }

    @Transactional
    public void deleteFilesForTask(Long taskId) {
        for (Attachment attachment : attachmentRepository.findByTaskId(taskId)) {
            try {
                Files.deleteIfExists(safeStoredPath(attachment.getStoragePath()));
            } catch (IOException exception) {
                throw new BusinessRuleException("Attachment file could not be deleted");
            }
        }
        attachmentRepository.deleteAll(attachmentRepository.findByTaskId(taskId));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessRuleException("Attachment must not be empty");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessRuleException("Attachment exceeds the maximum allowed size");
        }
    }

    private Path safeStoredPath(String storedPath) {
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(storageDirectory)) {
            throw new BusinessRuleException("Unsafe attachment path");
        }
        return path;
    }

    private String extension(String originalName) {
        String safeName = sanitizeOriginalName(originalName);
        int dot = safeName.lastIndexOf('.');
        return dot > 0 ? safeName.substring(dot).replaceAll("[^A-Za-z0-9.]", "") : "";
    }

    private String sanitizeOriginalName(String originalName) {
        String name = originalName == null ? "attachment" : Paths.get(originalName).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isBlank() ? "attachment" : name.substring(0, Math.min(name.length(), 255));
    }

    private String sanitizeHeaderFilename(String filename) {
        return sanitizeOriginalName(filename).replace("\"", "_").replace("\\", "_");
    }

    private String sanitizeContentType(String contentType) {
        if (contentType == null || !contentType.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private User findCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private Attachment findAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
    }

    private void requireMember(Task task, User user) {
        projectMemberRepository.findByProjectIdAndUserId(task.getProject().getId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getOriginalFileName(),
                attachment.getFileType(), attachment.getFileSize(), attachment.getUploadedBy().getId(),
                attachment.getUploadedAt());
    }
}
