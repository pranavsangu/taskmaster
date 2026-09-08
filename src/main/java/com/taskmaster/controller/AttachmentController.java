package com.taskmaster.controller;

import java.util.List;

import com.taskmaster.dto.response.AttachmentResponse;
import com.taskmaster.service.AttachmentService;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/api/tasks/{taskId}/attachments")
    public ResponseEntity<AttachmentResponse> upload(
            Authentication authentication,
            @PathVariable Long taskId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(CREATED).body(attachmentService.upload(authentication, taskId, file));
    }

    @GetMapping("/api/tasks/{taskId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> list(
            Authentication authentication, @PathVariable Long taskId) {
        return ResponseEntity.ok(attachmentService.list(authentication, taskId));
    }

    @GetMapping("/api/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            Authentication authentication, @PathVariable Long attachmentId) {
        return attachmentService.download(authentication, attachmentId);
    }

    @DeleteMapping("/api/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable Long attachmentId) {
        attachmentService.delete(authentication, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
