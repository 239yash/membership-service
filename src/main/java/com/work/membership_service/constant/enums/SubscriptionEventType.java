package com.work.membership_service.constant.enums;

// kinds of audit rows written to subscription_event
public enum SubscriptionEventType {
    CREATED,
    TIER_UPGRADED,
    TIER_DOWNGRADED,
    AUTO_PROMOTED,
    AUTO_DEMOTED,
    CANCELLED,
    RENEWED,
    EXPIRED
}
