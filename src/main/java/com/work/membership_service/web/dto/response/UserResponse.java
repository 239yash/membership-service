package com.work.membership_service.web.dto.response;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String name,
        String email,
        List<String> cohorts,
        Instant createdAt
) {
}
