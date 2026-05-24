package com.work.membership_service.engine.benefit.impl;

import com.work.membership_service.constant.enums.BenefitType;
import com.work.membership_service.engine.benefit.Benefit;
import com.work.membership_service.engine.benefit.BenefitOutcome;
import com.work.membership_service.engine.benefit.CartContext;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

// percent off, either on a list of categories or on everything ("*")
@RequiredArgsConstructor
public class ExtraDiscountBenefit implements Benefit {

    private static final String WILDCARD = "*";

    private final BigDecimal percent;
    private final Set<String> categories;

    @Override
    public BenefitType type() {
        return BenefitType.EXTRA_DISCOUNT;
    }

    @Override
    public BenefitOutcome apply(CartContext cart) {
        // sum of line prices that the discount applies to
        BigDecimal eligible = cart.items().stream()
                .filter(line -> categories.contains(WILDCARD) || categories.contains(line.category()))
                .map(CartContext.CartLine::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal savings = eligible
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        boolean applies = savings.signum() > 0;
        String scope = categories.contains(WILDCARD) ? "everything" : categories.toString();
        return new BenefitOutcome(
                BenefitType.EXTRA_DISCOUNT,
                applies,
                savings,
                percent.toPlainString() + "% off " + scope,
                Map.of("percent", percent, "categories", categories)
        );
    }
}
