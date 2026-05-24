package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.databind.JsonNode;

// admin creates a new criterion rule version
// ruleTree is the recursive json tree validated by CriterionEngine before persisting
public record CreateCriterionRequest(
        @NotNull(message = "ruleTree is required")
        JsonNode ruleTree,

        String description,

        String createdBy
) {
}
