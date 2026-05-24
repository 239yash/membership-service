package com.work.membership_service.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long orderId,
        Long userId,
        BigDecimal amount,
        String category,
        Instant placedAt,
        boolean tierEvaluated
) {
}
