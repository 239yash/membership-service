package com.work.membership_service.service.tier;

import com.work.membership_service.concurrency.StripedLockRegistry;
import com.work.membership_service.constant.enums.SubscriptionEventType;
import com.work.membership_service.constant.enums.SubscriptionStatus;
import com.work.membership_service.engine.criterion.CriterionEngine;
import com.work.membership_service.engine.stats.UserStats;
import com.work.membership_service.engine.stats.UserStatsProvider;
import com.work.membership_service.event.TierChangedEvent;
import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.model.entity.Subscription;
import com.work.membership_service.repository.SubscriptionRepository;
import com.work.membership_service.service.subscription.SubscriptionAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// re-evaluates a user's effective tier
// flow:
//   1. acquire per-user lock
//   2. load live sub (skip if none)
//   3. compute user stats
//   4. walk active tiers by rank desc; pick first whose rule tree matches
//   5. target = max(purchased rank, qualifying rank) -> never below the floor
//   6. if target != effective, update sub (@Version protected), audit, publish event
//
// retries up to 3 times on optimistic lock conflicts
@Service
@Slf4j
public class TierEvaluationService {

    // statuses that we still evaluate against — expired/cancelled subs are skipped
    private static final List<SubscriptionStatus> EVALUABLE_STATUSES = List.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PENDING_DOWNGRADE
    );

    private static final int MAX_ATTEMPTS = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final TierConfigService tierConfigService;
    private final CriterionEngine criterionEngine;
    private final UserStatsProvider userStatsProvider;
    private final SubscriptionAuditService auditService;
    private final StripedLockRegistry lockRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public TierEvaluationService(
            SubscriptionRepository subscriptionRepository,
            TierConfigService tierConfigService,
            CriterionEngine criterionEngine,
            UserStatsProvider userStatsProvider,
            SubscriptionAuditService auditService,
            StripedLockRegistry lockRegistry,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager txManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.tierConfigService = tierConfigService;
        this.criterionEngine = criterionEngine;
        this.userStatsProvider = userStatsProvider;
        this.auditService = auditService;
        this.lockRegistry = lockRegistry;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(txManager);
    }

    // evaluate one user — entry point for the listener and the sweep
    public void evaluate(Long userId) {
        try (StripedLockRegistry.LockHandle ignored = lockRegistry.acquire(userId)) {
            evaluateWithRetry(userId);
        }
    }

    // re-evaluate every live subscription
    // returns the number of tier changes applied
    public SweepResult runSweep() {
        List<Subscription> subs = subscriptionRepository.findAllByStatusIn(EVALUABLE_STATUSES);
        int changes = 0;
        for (Subscription s : subs) {
            try {
                Long before = s.getEffectiveTierId();
                evaluate(s.getUserId());
                Subscription after = subscriptionRepository.findById(s.getId()).orElse(null);
                if (after != null && !Objects.equals(before, after.getEffectiveTierId())) {
                    changes++;
                }
            } catch (Exception e) {
                log.error("[tier_sweep] eval failed user id: {}, err: {}", s.getUserId(), e.getMessage());
            }
        }
        log.info("[tier_sweep] evaluated subs: {}, tier changes: {}", subs.size(), changes);
        return new SweepResult(subs.size(), changes);
    }

    public record SweepResult(int evaluated, int tierChanges) {
    }

    // -- internals --

    private void evaluateWithRetry(Long userId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                tx.executeWithoutResult(status -> doEvaluate(userId));
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("[tier_eval] giving up after attempts: {}, user id: {}", MAX_ATTEMPTS, userId);
                    return;
                }
                log.debug("[tier_eval] version conflict, retrying attempt: {}, user id: {}", attempt + 1, userId);
                sleepBackoff(attempt);
            }
        }
    }

    private void doEvaluate(Long userId) {
        Optional<Subscription> subOpt = subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, EVALUABLE_STATUSES);
        if (subOpt.isEmpty()) {
            log.debug("[tier_eval] no live sub, skipping user id: {}", userId);
            return;
        }
        Subscription sub = subOpt.get();

        UserStats stats = userStatsProvider.compute(userId);
        List<TierConfig> configs = tierConfigService.getAllActive();
        if (configs.isEmpty()) {
            log.warn("[tier_eval] no active tiers configured, skipping user id: {}", userId);
            return;
        }

        // map of tier id -> rank for ordering comparisons
        Map<Long, Integer> rankById = new HashMap<>();
        for (TierConfig tc : configs) {
            rankById.put(tc.tierId(), tc.rank());
        }

        // first match wins, configs are already sorted rank desc
        TierConfig qualifying = null;
        for (TierConfig tc : configs) {
            if (criterionEngine.evaluate(tc.ruleTree(), stats)) {
                qualifying = tc;
                break;
            }
        }
        if (qualifying == null) {
            // safety net — silver's empty AND should always match, but just in case
            qualifying = configs.get(configs.size() - 1);
            log.warn("[tier_eval] no tier matched, falling back to lowest user id: {}, tier code: {}",
                    userId, qualifying.tierCode());
        }

        // purchased tier is the floor; effective is max(purchased, qualifying)
        Integer purchasedRank = rankById.get(sub.getPurchasedTierId());
        if (purchasedRank == null) {
            throw new NotFoundException("purchased tier missing from active configs id: " + sub.getPurchasedTierId());
        }

        Long targetTierId = qualifying.rank() >= purchasedRank
                ? qualifying.tierId()
                : sub.getPurchasedTierId();

        if (Objects.equals(targetTierId, sub.getEffectiveTierId())) {
            log.debug("[tier_eval] no change user id: {}, effective tier id: {}", userId, targetTierId);
            return;
        }

        Long fromTierId = sub.getEffectiveTierId();
        int fromRank = rankById.getOrDefault(fromTierId, -1);
        int toRank = rankById.getOrDefault(targetTierId, -1);
        SubscriptionEventType kind = toRank > fromRank
                ? SubscriptionEventType.AUTO_PROMOTED
                : SubscriptionEventType.AUTO_DEMOTED;

        sub.setEffectiveTierId(targetTierId);
        sub.setUpdatedAt(Instant.now());
        Subscription saved = subscriptionRepository.save(sub); // @Version may throw, caller retries

        String metadata = buildMetadata(qualifying);
        auditService.record(
                saved.getId(),
                kind,
                fromTierId,
                targetTierId,
                kind == SubscriptionEventType.AUTO_PROMOTED
                        ? "auto-promoted by tier rule"
                        : "auto-demoted by tier sweep",
                metadata
        );

        log.info("[tier_eval] {} user id: {}, sub id: {}, from tier id: {} to tier id: {}, rule id: {}",
                kind, userId, saved.getId(), fromTierId, targetTierId, qualifying.criterionRuleId());

        eventPublisher.publishEvent(new TierChangedEvent(
                saved.getId(), userId, fromTierId, targetTierId, kind));
    }

    private static String buildMetadata(TierConfig qualifying) {
        // small hand-built json so we dont pull ObjectMapper just for two fields
        return "{\"qualifyingTier\":\"" + qualifying.tierCode()
                + "\",\"criterionRuleId\":" + qualifying.criterionRuleId() + "}";
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
