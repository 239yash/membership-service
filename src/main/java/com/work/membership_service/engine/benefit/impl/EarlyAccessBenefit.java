package com.work.membership_service.engine.benefit.impl;

import com.work.membership_service.constant.enums.BenefitType;
import com.work.membership_service.engine.benefit.Benefit;
import com.work.membership_service.engine.benefit.BenefitOutcome;
import com.work.membership_service.engine.benefit.CartContext;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

// non-monetary benefit — lets the user shop sales X hours before public launch
@RequiredArgsConstructor
public class EarlyAccessBenefit implements Benefit {

    private final int hoursEarly;

    @Override
    public BenefitType type() {
        return BenefitType.EARLY_ACCESS;
    }

    @Override
    public BenefitOutcome apply(CartContext cart) {
        return new BenefitOutcome(
                BenefitType.EARLY_ACCESS,
                true,
                BigDecimal.ZERO,
                "early access of " + hoursEarly + "h",
                Map.of("hoursEarly", hoursEarly)
        );
    }
}
