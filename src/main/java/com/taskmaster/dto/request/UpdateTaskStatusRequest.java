package com.taskmaster.dto.request;

import com.taskmaster.enums.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull(message = "status must not be null")
        TaskStatus status
) {
}
