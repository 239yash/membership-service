package com.work.membership_service.engine.criterion.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.engine.criterion.CriterionDefinition;
import com.work.membership_service.engine.stats.UserStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// passes if the user's lifetime order value is at least `amount`
// params: { "amount": number }
@Component
public class MinLifetimeOrderValueCriterion implements CriterionDefinition {

    @Override
    public String type() {
        return "MIN_LIFETIME_ORDER_VALUE";
    }

    @Override
    public void validateParams(JsonNode params) {
        if (params == null || !params.has("amount")) {
            throw new IllegalArgumentException("MIN_LIFETIME_ORDER_VALUE requires {amount}");
        }
        if (new BigDecimal(params.get("amount").asText()).signum() < 0) {
            throw new IllegalArgumentException("MIN_LIFETIME_ORDER_VALUE amount must be >= 0");
        }
    }

    @Override
    public boolean evaluate(UserStats stats, JsonNode params) {
        BigDecimal required = new BigDecimal(params.get("amount").asText());
        return stats.lifetimeValue().compareTo(required) >= 0;
    }
}
