package com.taskmaster.dto.response;

import java.time.Instant;

import com.taskmaster.enums.ProjectRole;

public record ProjectMemberResponse(
        Long id,
        Long userId,
        String name,
        String email,
        ProjectRole role,
        Instant joinedAt
) {
}
