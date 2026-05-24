package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotBlank(message = "planCode is required")
        String planCode,

        @NotBlank(message = "tierCode is required")
        String tierCode
) {
}
