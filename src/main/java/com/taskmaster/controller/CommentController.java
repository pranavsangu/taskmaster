package com.taskmaster.controller;

import java.util.List;

import com.taskmaster.dto.request.CreateCommentRequest;
import com.taskmaster.dto.request.UpdateCommentRequest;
import com.taskmaster.dto.response.CommentResponse;
import com.taskmaster.service.CommentService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<CommentResponse> create(
            Authentication authentication,
            @PathVariable Long taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(CREATED).body(commentService.create(authentication, taskId, request));
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<List<CommentResponse>> list(
            Authentication authentication,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(commentService.list(authentication, taskId));
    }

    @PutMapping("/api/comments/{commentId}")
    public ResponseEntity<CommentResponse> update(
            Authentication authentication,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(commentService.update(authentication, commentId, request));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long commentId) {
        commentService.delete(authentication, commentId);
        return ResponseEntity.noContent().build();
    }
}
