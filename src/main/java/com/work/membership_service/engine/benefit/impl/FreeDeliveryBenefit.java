package com.work.membership_service.engine.benefit.impl;

import com.work.membership_service.constant.enums.BenefitType;
import com.work.membership_service.engine.benefit.Benefit;
import com.work.membership_service.engine.benefit.BenefitOutcome;
import com.work.membership_service.engine.benefit.CartContext;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

// waives the delivery fee when subtotal is at least minOrderValue
@RequiredArgsConstructor
public class FreeDeliveryBenefit implements Benefit {

    private final BigDecimal minOrderValue;

    @Override
    public BenefitType type() {
        return BenefitType.FREE_DELIVERY;
    }

    @Override
    public BenefitOutcome apply(CartContext cart) {
        boolean qualifies = cart.subtotal().compareTo(minOrderValue) >= 0;
        if (qualifies) {
            return new BenefitOutcome(
                    BenefitType.FREE_DELIVERY,
                    true,
                    cart.deliveryFee(),
                    "subtotal >= " + minOrderValue,
                    Map.of("minOrderValue", minOrderValue)
            );
        }
        return new BenefitOutcome(
                BenefitType.FREE_DELIVERY,
                false,
                BigDecimal.ZERO,
                "subtotal below minOrderValue " + minOrderValue,
                Map.of("minOrderValue", minOrderValue)
        );
    }
}
