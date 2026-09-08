package com.taskmaster.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must not exceed 200 characters")
        String name,

        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description
) {
}
