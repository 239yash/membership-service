package com.work.membership_service.event;

// fired by the admin activate flow when a tier's active rule or benefit pointer flips
// the cache invalidator listens and drops the affected redis entry
public record TierConfigChangedEvent(
        String tierCode
) {
}
