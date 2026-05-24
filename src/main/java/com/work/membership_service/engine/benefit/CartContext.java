package com.work.membership_service.engine.benefit;

import java.math.BigDecimal;
import java.util.List;

// input to benefit application — a snapshot of a cart at preview time
public record CartContext(
        Long userId,
        List<CartLine> items,
        BigDecimal subtotal,
        BigDecimal deliveryFee
) {

    public record CartLine(String category, BigDecimal price) {
    }
}
