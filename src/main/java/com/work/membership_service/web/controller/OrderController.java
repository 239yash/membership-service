package com.work.membership_service.web.controller;

import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.model.entity.OrderEntity;
import com.work.membership_service.service.order.OrderService;
import com.work.membership_service.web.dto.request.PlaceOrderRequest;
import com.work.membership_service.web.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @PathVariable Long userId,
            @Valid @RequestBody PlaceOrderRequest request) {

        log.info("[order_flow] receive order user id: {}, amount: {}, category: {}",
                userId, request.amount(), request.category());
        // placeOrder commits the order; the AFTER_COMMIT listener runs tier eval synchronously
        // before this line returns, so the caller sees the order + tier already updated
        OrderEntity placedOrder = orderService.placeOrder(userId, request.amount(), request.category());
        OrderResponse responseBody = new OrderResponse(
                placedOrder.getId(), placedOrder.getUserId(), placedOrder.getAmount(),
                placedOrder.getCategory(), placedOrder.getPlacedAt(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(responseBody));
    }
}
