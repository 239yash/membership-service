package com.work.membership_service.web.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        List<BenefitOutcomeResponse> appliedBenefits,
        BigDecimal totalSavings,
        BigDecimal finalPayable,
        String tierApplied
) {
}
