package com.work.membership_service.engine.stats;

// produces a stats handle for a user; impls decide how stats are sourced
public interface UserStatsProvider {

    UserStats compute(Long userId);
}
