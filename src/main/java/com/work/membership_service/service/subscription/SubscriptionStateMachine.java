package com.work.membership_service.service.subscription;

import com.work.membership_service.constant.enums.SubscriptionStatus;
import com.work.membership_service.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

// allowed status transitions for a subscription
// EXPIRED is terminal
@Component
public class SubscriptionStateMachine {

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(SubscriptionStatus.class);
        ALLOWED.put(SubscriptionStatus.ACTIVE, Set.of(
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PENDING_DOWNGRADE,
                SubscriptionStatus.CANCELLED_AT_PERIOD_END,
                SubscriptionStatus.EXPIRED
        ));
        ALLOWED.put(SubscriptionStatus.PENDING_DOWNGRADE, Set.of(
                // ACTIVE = user cancelled the pending downgrade (e.g., upgraded instead)
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PENDING_DOWNGRADE,
                SubscriptionStatus.CANCELLED_AT_PERIOD_END,
                SubscriptionStatus.EXPIRED
        ));
        ALLOWED.put(SubscriptionStatus.CANCELLED_AT_PERIOD_END, Set.of(
                SubscriptionStatus.CANCELLED_AT_PERIOD_END,
                SubscriptionStatus.EXPIRED
        ));
        ALLOWED.put(SubscriptionStatus.EXPIRED, Set.of(
                SubscriptionStatus.EXPIRED
        ));
    }

    public void assertCanTransition(SubscriptionStatus from, SubscriptionStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ConflictException(
                    "illegal subscription transition from: " + from + " to: " + to);
        }
    }
}
