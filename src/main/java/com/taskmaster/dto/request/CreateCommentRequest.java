package com.taskmaster.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "content must not be blank")
        @Size(max = 5000, message = "content must not exceed 5000 characters")
        String content
) {
}
