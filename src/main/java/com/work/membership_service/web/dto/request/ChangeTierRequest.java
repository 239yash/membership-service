package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangeTierRequest(
        @NotBlank(message = "newTierCode is required")
        String newTierCode
) {
}
