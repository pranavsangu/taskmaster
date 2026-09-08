package com.taskmaster.dto.response;

import java.time.Instant;

public record RegisterResponse(
        Long id,
        String name,
        String email,
        Instant createdAt
) {
}
