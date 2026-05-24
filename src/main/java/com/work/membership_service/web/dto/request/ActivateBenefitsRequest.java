package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record ActivateBenefitsRequest(
        @NotNull(message = "benefitConfigId is required")
        Long benefitConfigId
) {
}
