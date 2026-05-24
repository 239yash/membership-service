package com.work.membership_service.web.dto.response;

import com.work.membership_service.constant.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

// the live view of a subscription — codes resolved (not raw tier ids) for human readability
public record SubscriptionResponse(
        Long id,
        Long userId,
        String planCode,
        String purchasedTierCode,
        String effectiveTierCode,
        String scheduledTierCode,
        SubscriptionStatus status,
        Instant startDate,
        Instant endDate,
        Boolean autoRenew,
        BigDecimal pricePaid,
        Long version
) {
}
