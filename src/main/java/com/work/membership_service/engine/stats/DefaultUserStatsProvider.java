package com.work.membership_service.engine.stats;

import com.work.membership_service.model.entity.UserAccount;
import com.work.membership_service.repository.OrderRepository;
import com.work.membership_service.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

// loads cohorts from user_account and hands back a lazy stats object
// that pulls order aggregates from orders on demand
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultUserStatsProvider implements UserStatsProvider {

    private final UserAccountRepository userRepository;
    private final OrderRepository orderRepository;

    @Override
    public UserStats compute(Long userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found id: " + userId));

        // wrap the postgres text[] into a set
        Set<String> cohorts = new HashSet<>();
        if (user.getCohorts() != null) {
            for (String c : user.getCohorts()) {
                cohorts.add(c);
            }
        }

        log.debug("[user_stats] built stats handle for user id: {}, cohorts: {}", userId, cohorts);
        return new UserStats(userId, cohorts, orderRepository);
    }
}
