package com.taskmaster.controller;

import java.util.List;

import com.taskmaster.dto.request.AssignTaskRequest;
import com.taskmaster.dto.request.CreateTaskRequest;
import com.taskmaster.dto.request.UpdateTaskRequest;
import com.taskmaster.dto.request.UpdateTaskStatusRequest;
import com.taskmaster.dto.request.TaskQueryParams;
import com.taskmaster.dto.response.TaskResponse;
import com.taskmaster.dto.response.PageResponse;
import com.taskmaster.service.TaskService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(
            Authentication authentication,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(CREATED).body(taskService.create(authentication, projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<PageResponse<TaskResponse>> list(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(taskService.search(
                authentication,
                projectId,
                new TaskQueryParams(status, assignedTo, search, page, size, sort)));
    }

    @GetMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponse> get(
            Authentication authentication,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.get(authentication, taskId));
    }

    @PutMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponse> update(
            Authentication authentication,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.update(authentication, taskId, request));
    }

    @PatchMapping("/api/tasks/{taskId}/assignee")
    public ResponseEntity<TaskResponse> assign(
            Authentication authentication,
            @PathVariable Long taskId,
            @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(taskService.assign(authentication, taskId, request));
    }

    @PatchMapping("/api/tasks/{taskId}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            Authentication authentication,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        return ResponseEntity.ok(taskService.updateStatus(authentication, taskId, request));
    }

    @GetMapping("/api/tasks/my")
    public ResponseEntity<PageResponse<TaskResponse>> myTasks(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(taskService.searchMyTasks(
                authentication,
                new TaskQueryParams(status, assignedTo, search, page, size, sort)));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long taskId) {
        taskService.delete(authentication, taskId);
        return ResponseEntity.noContent().build();
    }
}
