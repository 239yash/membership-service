package com.work.membership_service.engine.benefit.impl;

import com.work.membership_service.constant.enums.BenefitType;
import com.work.membership_service.engine.benefit.Benefit;
import com.work.membership_service.engine.benefit.BenefitOutcome;
import com.work.membership_service.engine.benefit.CartContext;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

// non-monetary benefit — quotes a support sla in minutes
@RequiredArgsConstructor
public class PrioritySupportBenefit implements Benefit {

    private final int slaMinutes;

    @Override
    public BenefitType type() {
        return BenefitType.PRIORITY_SUPPORT;
    }

    @Override
    public BenefitOutcome apply(CartContext cart) {
        return new BenefitOutcome(
                BenefitType.PRIORITY_SUPPORT,
                true,
                BigDecimal.ZERO,
                "priority support sla " + slaMinutes + " min",
                Map.of("slaMinutes", slaMinutes)
        );
    }
}
