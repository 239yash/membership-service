package com.work.membership_service.web.dto.response;

import java.time.Instant;

// returned after admin activates a new rule or benefit config on a tier
public record ActivationResponse(
        String tierCode,
        String configType,
        Long previousVersionId,
        Long activeVersionId,
        Instant activatedAt
) {
}
