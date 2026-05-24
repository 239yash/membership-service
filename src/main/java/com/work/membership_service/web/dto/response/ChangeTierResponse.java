package com.work.membership_service.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

// what the caller gets back from POST /change-tier
// transition is UPGRADE or DOWNGRADE
public record ChangeTierResponse(
        Long subscriptionId,
        String transition,
        String previousTierCode,
        String newTierCode,
        String scheduledTierCode,
        boolean appliedImmediately,
        BigDecimal proratedCharge,
        Instant effectiveFrom,
        Instant scheduledFor
) {
}
