package com.work.membership_service.web.dto.response;

import java.time.Instant;

public record CriterionCreatedResponse(
        Long id,
        String description,
        String createdBy,
        Instant createdAt
) {
}
