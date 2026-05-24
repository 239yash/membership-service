package com.work.membership_service.web.dto.response;

import com.work.membership_service.constant.enums.BillingFrequency;

import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String code,
        String name,
        BillingFrequency billingFrequency,
        Integer durationDays,
        BigDecimal basePrice,
        Boolean active
) {
}
