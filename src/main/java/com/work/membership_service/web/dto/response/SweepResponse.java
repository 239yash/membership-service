package com.work.membership_service.web.dto.response;

public record SweepResponse(
        int subscriptionsEvaluated,
        int tierChanges,
        long durationMs
) {
}
