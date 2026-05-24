package com.work.membership_service.event;

import com.work.membership_service.constant.enums.SubscriptionEventType;

// fired by TierEvaluationService whenever a sub's effective tier moves
// downstream listeners (notifications, analytics, coupons) can hang off this
public record TierChangedEvent(
        Long subscriptionId,
        Long userId,
        Long fromTierId,
        Long toTierId,
        SubscriptionEventType kind
) {
}
