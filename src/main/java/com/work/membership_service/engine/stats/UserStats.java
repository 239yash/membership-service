package com.work.membership_service.engine.stats;

import com.work.membership_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// stats for one user, lazily computed and cached for the duration of one tier evaluation
// each rolling-window query (count or sum for last N days) hits the db at most once per N
@RequiredArgsConstructor
public class UserStats {

    private final Long userId;
    private final Set<String> cohorts;
    private final OrderRepository orderRepository;

    private final Map<Integer, Long> countByWindow = new HashMap<>();
    private final Map<Integer, BigDecimal> sumByWindow = new HashMap<>();
    private Long lifetimeCountCache;
    private BigDecimal lifetimeValueCache;

    public Long userId() {
        return userId;
    }

    public Set<String> cohorts() {
        return cohorts;
    }

    public long countInLastDays(int days) {
        return countByWindow.computeIfAbsent(days, d ->
                orderRepository.countByUserIdAndPlacedAtAfter(userId, since(d)));
    }

    public BigDecimal sumInLastDays(int days) {
        return sumByWindow.computeIfAbsent(days, d ->
                orderRepository.sumAmountByUserIdAndPlacedAtAfter(userId, since(d)));
    }

    public long lifetimeCount() {
        if (lifetimeCountCache == null) {
            lifetimeCountCache = orderRepository.countByUserId(userId);
        }
        return lifetimeCountCache;
    }

    public BigDecimal lifetimeValue() {
        if (lifetimeValueCache == null) {
            lifetimeValueCache = orderRepository.sumAmountByUserId(userId);
        }
        return lifetimeValueCache;
    }

    private static Instant since(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
