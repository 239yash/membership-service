package com.work.membership_service.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewRequest(
        @Valid
        @NotEmpty(message = "items must not be empty")
        List<CartLineDto> items,

        @NotNull(message = "deliveryFee is required")
        @PositiveOrZero(message = "deliveryFee must be >= 0")
        BigDecimal deliveryFee
) {

    public record CartLineDto(
            String category,

            @NotNull(message = "price is required")
            @PositiveOrZero(message = "price must be >= 0")
            BigDecimal price
    ) {
    }
}
