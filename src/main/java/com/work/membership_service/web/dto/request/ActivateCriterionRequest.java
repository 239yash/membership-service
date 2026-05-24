package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record ActivateCriterionRequest(
        @NotNull(message = "criterionRuleId is required")
        Long criterionRuleId
) {
}
