package com.work.membership_service.engine.criterion.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.engine.criterion.CriterionDefinition;
import com.work.membership_service.engine.stats.UserStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// passes if total order value in the last `windowDays` days is at least `amount`
// params: { "amount": number, "windowDays": int }
@Component
public class MinOrderValueCriterion implements CriterionDefinition {

    @Override
    public String type() {
        return "MIN_ORDER_VALUE";
    }

    @Override
    public void validateParams(JsonNode params) {
        if (params == null || !params.has("amount") || !params.has("windowDays")) {
            throw new IllegalArgumentException("MIN_ORDER_VALUE requires {amount, windowDays}");
        }
        if (new BigDecimal(params.get("amount").asText()).signum() < 0
                || params.get("windowDays").asInt() <= 0) {
            throw new IllegalArgumentException("MIN_ORDER_VALUE amount must be >= 0 and windowDays > 0");
        }
    }

    @Override
    public boolean evaluate(UserStats stats, JsonNode params) {
        BigDecimal required = new BigDecimal(params.get("amount").asText());
        int window = params.get("windowDays").asInt();
        return stats.sumInLastDays(window).compareTo(required) >= 0;
    }
}
