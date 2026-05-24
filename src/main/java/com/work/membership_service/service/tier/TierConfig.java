package com.work.membership_service.service.tier;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

// the cacheable view of one tier — what gets stored in redis as json
// holds parsed json for the rule tree and the benefits array
public record TierConfig(
        Long tierId,
        String tierCode,
        String tierName,
        Integer rank,
        BigDecimal priceMultiplier,
        Long criterionRuleId,
        JsonNode ruleTree,
        Long benefitConfigId,
        JsonNode benefits
) {
}
