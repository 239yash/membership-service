package com.work.membership_service.web.dto.response;

import com.work.membership_service.constant.enums.BenefitType;

import java.math.BigDecimal;
import java.util.Map;

public record BenefitOutcomeResponse(
        BenefitType type,
        boolean applies,
        BigDecimal savings,
        String reason,
        Map<String, Object> metadata
) {
}
