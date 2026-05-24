package com.work.membership_service.constant.enums;

// live state of a subscription
public enum SubscriptionStatus {
    // currently in its paid period, benefits live
    ACTIVE,

    // user asked to downgrade, takes effect at period end
    PENDING_DOWNGRADE,

    // user asked to cancel, benefits keep working until period end
    CANCELLED_AT_PERIOD_END,

    // past end date, no longer live
    EXPIRED
}
