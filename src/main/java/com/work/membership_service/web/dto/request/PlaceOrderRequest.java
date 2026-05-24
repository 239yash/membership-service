package com.work.membership_service.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be > 0")
        BigDecimal amount,

        String category
) {
}
