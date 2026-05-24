package com.work.membership_service.service.subscription.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

// works out the prorated charge when a user upgrades mid-period
// formula: (newPrice - oldPrice) * daysRemaining / totalDays
// downgrades produce no immediate charge — they take effect at period end
@Component
public class ProrationCalculator {

    public BigDecimal upgradeProration(
            BigDecimal oldPrice,
            BigDecimal newPrice,
            Instant now,
            Instant periodStart,
            Instant periodEnd) {

        long totalDays = Math.max(1, Duration.between(periodStart, periodEnd).toDays());
        long remaining = Math.max(0, Duration.between(now, periodEnd).toDays());

        BigDecimal delta = newPrice.subtract(oldPrice);
        if (delta.signum() <= 0 || remaining == 0) {
            return BigDecimal.ZERO;
        }

        return delta
                .multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
    }
}
