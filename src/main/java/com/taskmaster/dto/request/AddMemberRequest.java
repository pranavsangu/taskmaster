package com.taskmaster.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be valid")
        @Size(max = 320, message = "email must not exceed 320 characters")
        String email
) {
}
