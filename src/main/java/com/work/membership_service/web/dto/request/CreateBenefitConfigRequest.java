package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.databind.JsonNode;

// admin creates a new benefit config version
// benefits is a json array of {type, params} validated by BenefitFactory before persisting
public record CreateBenefitConfigRequest(
        @NotNull(message = "benefits is required")
        JsonNode benefits,

        String description,

        String createdBy
) {
}
