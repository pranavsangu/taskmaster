package com.taskmaster.service;

import java.util.List;
import java.util.Locale;

import com.taskmaster.domain.Comment;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.dto.request.CreateCommentRequest;
import com.taskmaster.dto.request.UpdateCommentRequest;
import com.taskmaster.dto.response.CommentResponse;
import com.taskmaster.exception.ForbiddenException;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.CommentRepository;
import com.taskmaster.repository.ProjectMemberRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public CommentService(
            CommentRepository commentRepository,
            ProjectMemberRepository projectMemberRepository,
            TaskRepository taskRepository,
            UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CommentResponse create(Authentication authentication, Long taskId, CreateCommentRequest request) {
        User author = findCurrentUser(authentication);
        Task task = findTask(taskId);
        requireMember(task, author);
        Comment comment = new Comment(task, author, request.content().trim());
        task.addComment(comment);
        return toResponse(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Authentication authentication, Long taskId) {
        User user = findCurrentUser(authentication);
        Task task = findTask(taskId);
        requireMember(task, user);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse update(
            Authentication authentication,
            Long commentId,
            UpdateCommentRequest request) {
        User user = findCurrentUser(authentication);
        Comment comment = findComment(commentId);
        requireMember(comment.getTask(), user);
        requireAuthor(comment, user);
        comment.setContent(request.content().trim());
        return toResponse(commentRepository.save(comment));
    }

    @Transactional
    public void delete(Authentication authentication, Long commentId) {
        User user = findCurrentUser(authentication);
        Comment comment = findComment(commentId);
        requireMember(comment.getTask(), user);
        requireAuthor(comment, user);
        commentRepository.delete(comment);
    }

    private User findCurrentUser(Authentication authentication) {
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
    }

    private void requireMember(Task task, User user) {
        projectMemberRepository.findByProjectIdAndUserId(task.getProject().getId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private void requireAuthor(Comment comment, User user) {
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the comment author can modify this comment");
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
