package com.taskmaster.dto.response;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        String originalFileName,
        String fileType,
        long fileSize,
        Long uploadedBy,
        Instant uploadedAt
) {
}
