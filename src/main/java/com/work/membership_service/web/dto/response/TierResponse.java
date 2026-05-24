package com.work.membership_service.web.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

// the public view of a tier — metadata + the currently active rule + benefits
public record TierResponse(
        Long id,
        String code,
        String name,
        Integer rank,
        BigDecimal priceMultiplier,
        Long activeCriterionRuleId,
        JsonNode ruleTree,
        Long activeBenefitConfigId,
        JsonNode benefits
) {
}
