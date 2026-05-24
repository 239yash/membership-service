package com.work.membership_service.web.controller;

import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.engine.benefit.CartContext;
import com.work.membership_service.service.checkout.CheckoutService;
import com.work.membership_service.web.dto.request.CheckoutPreviewRequest;
import com.work.membership_service.web.dto.response.BenefitOutcomeResponse;
import com.work.membership_service.web.dto.response.CheckoutPreviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/checkout")
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/preview")
    public ApiResponse<CheckoutPreviewResponse> preview(
            @PathVariable Long userId,
            @Valid @RequestBody CheckoutPreviewRequest request) {

        log.debug("[checkout] preview user id: {}, items: {}, deliveryFee: {}",
                userId, request.items().size(), request.deliveryFee());

        List<CartContext.CartLine> cartLines = request.items().stream()
                .map(line -> new CartContext.CartLine(line.category(), line.price()))
                .toList();

        CheckoutService.PreviewOutcome outcome =
                checkoutService.preview(userId, cartLines, request.deliveryFee());

        List<BenefitOutcomeResponse> appliedBenefits = outcome.outcomes().stream()
                .map(benefitOutcome -> new BenefitOutcomeResponse(
                        benefitOutcome.type(),
                        benefitOutcome.applies(),
                        benefitOutcome.savings(),
                        benefitOutcome.reason(),
                        benefitOutcome.metadata()))
                .toList();

        return ApiResponse.ok(new CheckoutPreviewResponse(
                outcome.subtotal(),
                outcome.deliveryFee(),
                appliedBenefits,
                outcome.totalSavings(),
                outcome.finalPayable(),
                outcome.tierAppliedCode()
        ));
    }
}
