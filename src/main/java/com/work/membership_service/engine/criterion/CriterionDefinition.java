package com.work.membership_service.engine.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.engine.stats.UserStats;

// one criterion type, e.g. MIN_ORDER_COUNT
// implementations are spring beans; they self-register into the registry
public interface CriterionDefinition {

    // unique string used in the json rule tree, e.g. "MIN_ORDER_COUNT"
    String type();

    // throw IllegalArgumentException if params are missing or malformed
    // called at write time so bad rules never reach the evaluator
    void validateParams(JsonNode params);

    // evaluate the criterion against the user's stats
    boolean evaluate(UserStats stats, JsonNode params);
}
