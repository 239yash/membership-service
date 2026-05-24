package com.work.membership_service.engine.criterion.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.engine.criterion.CriterionDefinition;
import com.work.membership_service.engine.stats.UserStats;
import org.springframework.stereotype.Component;

// passes if the user placed at least `count` orders in the last `windowDays` days
// params: { "count": int, "windowDays": int }
@Component
public class MinOrderCountCriterion implements CriterionDefinition {

    @Override
    public String type() {
        return "MIN_ORDER_COUNT";
    }

    @Override
    public void validateParams(JsonNode params) {
        if (params == null || !params.has("count") || !params.has("windowDays")) {
            throw new IllegalArgumentException("MIN_ORDER_COUNT requires {count, windowDays}");
        }
        if (params.get("count").asInt() <= 0 || params.get("windowDays").asInt() <= 0) {
            throw new IllegalArgumentException("MIN_ORDER_COUNT count and windowDays must be > 0");
        }
    }

    @Override
    public boolean evaluate(UserStats stats, JsonNode params) {
        long required = params.get("count").asLong();
        int window = params.get("windowDays").asInt();
        return stats.countInLastDays(window) >= required;
    }
}
