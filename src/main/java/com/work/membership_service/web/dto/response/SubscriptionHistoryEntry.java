package com.work.membership_service.web.dto.response;

import java.util.List;

// one row in the subscription history payload — the subscription + its audit events
public record SubscriptionHistoryEntry(
        SubscriptionResponse subscription,
        List<SubscriptionEventResponse> events
) {
}
