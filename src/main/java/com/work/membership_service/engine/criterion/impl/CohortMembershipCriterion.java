package com.work.membership_service.engine.criterion.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.engine.criterion.CriterionDefinition;
import com.work.membership_service.engine.stats.UserStats;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

// passes if the user belongs to at least one of the listed cohorts
// params: { "cohorts": [ ... ] }
@Component
public class CohortMembershipCriterion implements CriterionDefinition {

    @Override
    public String type() {
        return "COHORT_MEMBERSHIP";
    }

    @Override
    public void validateParams(JsonNode params) {
        if (params == null || !params.has("cohorts") || !params.get("cohorts").isArray()) {
            throw new IllegalArgumentException("COHORT_MEMBERSHIP requires {cohorts: []}");
        }
        if (params.get("cohorts").isEmpty()) {
            throw new IllegalArgumentException("COHORT_MEMBERSHIP cohorts array must not be empty");
        }
    }

    @Override
    public boolean evaluate(UserStats stats, JsonNode params) {
        Set<String> required = new HashSet<>();
        params.get("cohorts").forEach(n -> required.add(n.asText()));
        for (String c : stats.cohorts()) {
            if (required.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
