package com.taskmaster.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import com.taskmaster.enums.TaskStatus;

public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDate dueDate,
        TaskStatus status,
        Long projectId,
        Long createdByUserId,
        Long assignedToUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
