package com.work.membership_service.engine.benefit;

import com.work.membership_service.constant.enums.BenefitType;

import java.math.BigDecimal;
import java.util.Map;

// the result of applying one benefit to a cart
// savings is monetary, metadata is freeform for non-monetary benefits
public record BenefitOutcome(
        BenefitType type,
        boolean applies,
        BigDecimal savings,
        String reason,
        Map<String, Object> metadata
) {
}
