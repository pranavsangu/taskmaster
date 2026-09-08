package com.taskmaster.dto.request;

import jakarta.validation.constraints.Positive;

public record AssignTaskRequest(
        @Positive(message = "userId must be positive")
        Long userId
) {
}
