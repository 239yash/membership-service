package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @Email(message = "email must be valid")
        @NotBlank(message = "email must not be blank")
        String email,

        List<String> cohorts
) {
}
