package com.work.membership_service.service.checkout;

import com.work.membership_service.engine.benefit.Benefit;
import com.work.membership_service.engine.benefit.BenefitFactory;
import com.work.membership_service.engine.benefit.BenefitOutcome;
import com.work.membership_service.engine.benefit.CartContext;
import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.model.entity.MembershipTier;
import com.work.membership_service.model.entity.Subscription;
import com.work.membership_service.repository.MembershipTierRepository;
import com.work.membership_service.service.subscription.SubscriptionService;
import com.work.membership_service.service.tier.TierConfig;
import com.work.membership_service.service.tier.TierConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// applies the user's effective tier benefits to a sample cart
// users without a live subscription get no benefits but the call still succeeds
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final SubscriptionService subscriptionService;
    private final TierConfigService tierConfigService;
    private final BenefitFactory benefitFactory;
    private final MembershipTierRepository tierRepository;

    public PreviewOutcome preview(Long userId, List<CartContext.CartLine> cartLines, BigDecimal deliveryFee) {
        BigDecimal subtotal = cartLines.stream()
                .map(CartContext.CartLine::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CartContext cartContext = new CartContext(userId, cartLines, subtotal, deliveryFee);

        Optional<Subscription> liveSubscription = subscriptionService.findLiveForUser(userId);
        if (liveSubscription.isEmpty()) {
            log.info("[checkout] no live subscription user id: {}, returning full price", userId);
            return new PreviewOutcome(subtotal, deliveryFee, List.of(), BigDecimal.ZERO,
                    subtotal.add(deliveryFee), null);
        }

        Subscription subscription = liveSubscription.get();
        MembershipTier effectiveTier = tierRepository.findById(subscription.getEffectiveTierId())
                .orElseThrow(() -> new NotFoundException(
                        "effective tier not found id: " + subscription.getEffectiveTierId()));

        TierConfig effectiveTierConfig = tierConfigService.getByCode(effectiveTier.getCode());
        List<Benefit> benefits = benefitFactory.build(effectiveTierConfig.benefits());

        List<BenefitOutcome> appliedOutcomes = new ArrayList<>();
        BigDecimal totalSavings = BigDecimal.ZERO;
        for (Benefit benefit : benefits) {
            BenefitOutcome outcome = benefit.apply(cartContext);
            appliedOutcomes.add(outcome);
            if (outcome.applies()) {
                totalSavings = totalSavings.add(outcome.savings());
            }
        }

        BigDecimal finalPayable = subtotal.add(deliveryFee).subtract(totalSavings);
        log.info("[checkout] preview user id: {}, tier: {}, subtotal: {}, savings: {}, payable: {}",
                userId, effectiveTier.getCode(), subtotal, totalSavings, finalPayable);

        return new PreviewOutcome(subtotal, deliveryFee, appliedOutcomes, totalSavings, finalPayable,
                effectiveTier.getCode());
    }

    // intermediate record so the controller can map to the wire dto without leaking entities
    public record PreviewOutcome(
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            List<BenefitOutcome> outcomes,
            BigDecimal totalSavings,
            BigDecimal finalPayable,
            String tierAppliedCode
    ) {
    }
}
