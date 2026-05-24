package com.work.membership_service.event;

import java.math.BigDecimal;
import java.time.Instant;

// fired by OrderService after a new order is persisted
// the tier eval listener consumes this after the transaction commits
public record OrderPlacedEvent(
        Long orderId,
        Long userId,
        BigDecimal amount,
        String category,
        Instant placedAt
) {
}
