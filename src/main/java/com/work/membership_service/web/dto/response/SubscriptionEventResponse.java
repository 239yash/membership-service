package com.work.membership_service.web.dto.response;

import com.work.membership_service.constant.enums.SubscriptionEventType;

import java.time.Instant;

public record SubscriptionEventResponse(
        Long id,
        SubscriptionEventType type,
        String fromTierCode,
        String toTierCode,
        String reason,
        String metadata,
        Instant occurredAt
) {
}
