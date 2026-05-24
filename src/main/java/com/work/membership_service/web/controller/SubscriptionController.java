package com.work.membership_service.web.controller;

import com.work.membership_service.constant.enums.SubscriptionStatus;
import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.model.entity.MembershipPlan;
import com.work.membership_service.model.entity.MembershipTier;
import com.work.membership_service.model.entity.Subscription;
import com.work.membership_service.model.entity.SubscriptionEvent;
import com.work.membership_service.repository.MembershipPlanRepository;
import com.work.membership_service.repository.MembershipTierRepository;
import com.work.membership_service.repository.SubscriptionEventRepository;
import com.work.membership_service.service.subscription.SubscriptionService;
import com.work.membership_service.web.dto.request.ChangeTierRequest;
import com.work.membership_service.web.dto.request.SubscribeRequest;
import com.work.membership_service.web.dto.response.ChangeTierResponse;
import com.work.membership_service.web.dto.response.SubscriptionEventResponse;
import com.work.membership_service.web.dto.response.SubscriptionHistoryEntry;
import com.work.membership_service.web.dto.response.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final MembershipTierRepository tierRepository;
    private final MembershipPlanRepository planRepository;

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        log.info("[sub_lifecycle] subscribe request user id: {}, plan: {}, tier: {}",
                request.userId(), request.planCode(), request.tierCode());
        Subscription subscription = subscriptionService.subscribe(
                request.userId(), request.planCode(), request.tierCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(subscription)));
    }

    @GetMapping("/users/{userId}/subscription")
    public ApiResponse<SubscriptionResponse> getActive(@PathVariable Long userId) {
        log.debug("[sub_lifecycle] get active user id: {}", userId);
        Subscription subscription = subscriptionService.findLiveForUser(userId)
                .orElseThrow(() -> new NotFoundException("no live subscription for user id: " + userId));
        return ApiResponse.ok(toResponse(subscription));
    }

    @GetMapping("/users/{userId}/subscriptions")
    public ApiResponse<List<SubscriptionHistoryEntry>> getHistory(@PathVariable Long userId) {
        log.debug("[sub_lifecycle] get history user id: {}", userId);
        List<Subscription> subscriptions = subscriptionService.findHistoryForUser(userId);
        List<SubscriptionHistoryEntry> entries = subscriptions.stream()
                .map(subscription -> new SubscriptionHistoryEntry(
                        toResponse(subscription),
                        eventsForSubscription(subscription.getId())))
                .toList();
        return ApiResponse.ok(entries);
    }

    @PostMapping("/subscriptions/{subscriptionId}/change-tier")
    public ApiResponse<ChangeTierResponse> changeTier(
            @PathVariable Long subscriptionId,
            @Valid @RequestBody ChangeTierRequest request) {

        Subscription beforeChange = subscriptionService.findByIdOrThrow(subscriptionId);
        Long previousPurchasedTierId = beforeChange.getPurchasedTierId();

        Subscription afterChange = subscriptionService.changeTier(subscriptionId, request.newTierCode());

        boolean isUpgrade = afterChange.getStatus() != SubscriptionStatus.PENDING_DOWNGRADE;
        String previousTierCode = tierCodeFor(previousPurchasedTierId);
        String newTierCode;
        String scheduledTierCode = null;
        BigDecimal proratedCharge;
        Instant effectiveFrom = null;
        Instant scheduledFor = null;

        if (isUpgrade) {
            newTierCode = tierCodeFor(afterChange.getPurchasedTierId());
            proratedCharge = afterChange.getPricePaid().subtract(beforeChange.getPricePaid());
            effectiveFrom = afterChange.getUpdatedAt();
        } else {
            newTierCode = tierCodeFor(afterChange.getScheduledTierId());
            scheduledTierCode = newTierCode;
            proratedCharge = BigDecimal.ZERO;
            scheduledFor = afterChange.getEndDate();
        }

        ChangeTierResponse responseBody = new ChangeTierResponse(
                afterChange.getId(),
                isUpgrade ? "UPGRADE" : "DOWNGRADE",
                previousTierCode,
                newTierCode,
                scheduledTierCode,
                isUpgrade,
                proratedCharge,
                effectiveFrom,
                scheduledFor
        );
        return ApiResponse.ok(responseBody);
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public ApiResponse<SubscriptionResponse> cancel(@PathVariable Long subscriptionId) {
        log.info("[sub_lifecycle] cancel request subscription id: {}", subscriptionId);
        Subscription cancelled = subscriptionService.cancel(subscriptionId);
        return ApiResponse.ok(toResponse(cancelled));
    }

    // -- mapping helpers --

    private SubscriptionResponse toResponse(Subscription subscription) {
        String planCode = planRepository.findById(subscription.getPlanId())
                .map(MembershipPlan::getCode).orElse(null);
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getUserId(),
                planCode,
                tierCodeFor(subscription.getPurchasedTierId()),
                tierCodeFor(subscription.getEffectiveTierId()),
                subscription.getScheduledTierId() == null ? null : tierCodeFor(subscription.getScheduledTierId()),
                subscription.getStatus(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getAutoRenew(),
                subscription.getPricePaid(),
                subscription.getVersion()
        );
    }

    private String tierCodeFor(Long tierId) {
        if (tierId == null) {
            return null;
        }
        return tierRepository.findById(tierId).map(MembershipTier::getCode).orElse(null);
    }

    private List<SubscriptionEventResponse> eventsForSubscription(Long subscriptionId) {
        // resolve every tier id up front so we dont hit the repo once per event row
        Map<Long, String> tierCodeById = new HashMap<>();
        List<SubscriptionEvent> events =
                subscriptionEventRepository.findAllBySubscriptionIdOrderByOccurredAtDesc(subscriptionId);
        for (SubscriptionEvent event : events) {
            cacheTierCode(tierCodeById, event.getFromTierId());
            cacheTierCode(tierCodeById, event.getToTierId());
        }
        return events.stream()
                .map(event -> new SubscriptionEventResponse(
                        event.getId(),
                        event.getType(),
                        event.getFromTierId() == null ? null : tierCodeById.get(event.getFromTierId()),
                        event.getToTierId() == null ? null : tierCodeById.get(event.getToTierId()),
                        event.getReason(),
                        event.getMetadata(),
                        event.getOccurredAt()
                ))
                .toList();
    }

    private void cacheTierCode(Map<Long, String> cache, Long tierId) {
        if (tierId == null || cache.containsKey(tierId)) {
            return;
        }
        Optional<MembershipTier> tier = tierRepository.findById(tierId);
        tier.ifPresent(t -> cache.put(tierId, t.getCode()));
    }
}
