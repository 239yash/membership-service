package com.work.membership_service.engine.benefit.impl;

import com.work.membership_service.constant.enums.BenefitType;
import com.work.membership_service.engine.benefit.Benefit;
import com.work.membership_service.engine.benefit.BenefitOutcome;
import com.work.membership_service.engine.benefit.CartContext;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// non-monetary benefit — exposes a list of deal ids the user can access
@RequiredArgsConstructor
public class ExclusiveDealsBenefit implements Benefit {

    private final List<String> dealIds;

    @Override
    public BenefitType type() {
        return BenefitType.EXCLUSIVE_DEALS;
    }

    @Override
    public BenefitOutcome apply(CartContext cart) {
        return new BenefitOutcome(
                BenefitType.EXCLUSIVE_DEALS,
                true,
                BigDecimal.ZERO,
                "exclusive deals unlocked",
                Map.of("dealIds", dealIds)
        );
    }
}
