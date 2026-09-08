package com.taskmaster.dto.response;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long taskId,
        Long authorId,
        String authorName,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
}
