package com.work.membership_service.service.subscription;

import com.work.membership_service.concurrency.StripedLockRegistry;
import com.work.membership_service.constant.enums.SubscriptionEventType;
import com.work.membership_service.constant.enums.SubscriptionStatus;
import com.work.membership_service.exception.ConflictException;
import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.exception.ValidationException;
import com.work.membership_service.model.entity.MembershipPlan;
import com.work.membership_service.model.entity.MembershipTier;
import com.work.membership_service.model.entity.Subscription;
import com.work.membership_service.repository.MembershipPlanRepository;
import com.work.membership_service.repository.MembershipTierRepository;
import com.work.membership_service.repository.SubscriptionRepository;
import com.work.membership_service.repository.UserAccountRepository;
import com.work.membership_service.service.subscription.pricing.ProrationCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// the lifecycle service: subscribe, change tier (up/down), cancel
// concurrency story:
//   1. per-user striped lock — serializes operations on the same user within this jvm
//   2. @Version on Subscription — last-mile optimistic lock across jvms / when the lock is bypassed
//
// the lock is acquired OUTSIDE the transaction so commit happens before the next caller sees the row
@Service
@Slf4j
public class SubscriptionService {

    // statuses that count as "live" — a user with any of these cannot subscribe again
    private static final List<SubscriptionStatus> LIVE_STATUSES = List.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PENDING_DOWNGRADE,
            SubscriptionStatus.CANCELLED_AT_PERIOD_END
    );

    private final SubscriptionRepository subscriptionRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipTierRepository tierRepository;
    private final UserAccountRepository userRepository;
    private final SubscriptionAuditService auditService;
    private final SubscriptionStateMachine stateMachine;
    private final ProrationCalculator prorationCalculator;
    private final StripedLockRegistry lockRegistry;
    private final TransactionTemplate tx;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            MembershipPlanRepository planRepository,
            MembershipTierRepository tierRepository,
            UserAccountRepository userRepository,
            SubscriptionAuditService auditService,
            SubscriptionStateMachine stateMachine,
            ProrationCalculator prorationCalculator,
            StripedLockRegistry lockRegistry,
            PlatformTransactionManager txManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.tierRepository = tierRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.stateMachine = stateMachine;
        this.prorationCalculator = prorationCalculator;
        this.lockRegistry = lockRegistry;
        // programmatic tx so the lock can wrap it (commit -> unlock ordering)
        this.tx = new TransactionTemplate(txManager);
    }

    // -- read paths --

    public Optional<Subscription> findLiveForUser(Long userId) {
        return subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, LIVE_STATUSES);
    }

    public List<Subscription> findHistoryForUser(Long userId) {
        return subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public Subscription findByIdOrThrow(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("subscription not found id: " + subscriptionId));
    }

    // -- write paths --

    public Subscription subscribe(Long userId, String planCode, String tierCode) {
        try (StripedLockRegistry.LockHandle ignored = lockRegistry.acquire(userId)) {
            return tx.execute(status -> doSubscribe(userId, planCode, tierCode));
        }
    }

    public Subscription changeTier(Long subscriptionId, String newTierCode) {
        // one read outside the lock just to learn whose stripe to lock on
        Subscription existingSubscription = findByIdOrThrow(subscriptionId);
        try (StripedLockRegistry.LockHandle ignored = lockRegistry.acquire(existingSubscription.getUserId())) {
            return tx.execute(status -> doChangeTier(subscriptionId, newTierCode));
        }
    }

    public Subscription cancel(Long subscriptionId) {
        Subscription existingSubscription = findByIdOrThrow(subscriptionId);
        try (StripedLockRegistry.LockHandle ignored = lockRegistry.acquire(existingSubscription.getUserId())) {
            return tx.execute(status -> doCancel(subscriptionId));
        }
    }

    // -- internals (run inside the tx, lock already held) --

    private Subscription doSubscribe(Long userId, String planCode, String tierCode) {
        // refuse if user already has a live subscription
        findLiveForUser(userId).ifPresent(existing -> {
            throw new ConflictException(
                    "user already has a live subscription id: " + existing.getId());
        });

        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user not found id: " + userId));

        MembershipPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new NotFoundException("plan not found code: " + planCode));
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new ValidationException("plan inactive code: " + planCode);
        }

        MembershipTier tier = tierRepository.findByCode(tierCode)
                .orElseThrow(() -> new NotFoundException("tier not found code: " + tierCode));
        if (!Boolean.TRUE.equals(tier.getActive())) {
            throw new ValidationException("tier inactive code: " + tierCode);
        }

        BigDecimal price = plan.getBasePrice().multiply(tier.getPriceMultiplier());
        Instant now = Instant.now();
        Instant end = now.plus(plan.getDurationDays(), ChronoUnit.DAYS);

        Subscription subscription = Subscription.builder()
                .userId(userId)
                .planId(plan.getId())
                .purchasedTierId(tier.getId())
                .effectiveTierId(tier.getId())
                .scheduledTierId(null)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(end)
                .autoRenew(true)
                .pricePaid(price)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Subscription saved;
        try {
            saved = subscriptionRepository.save(subscription);
        } catch (OptimisticLockingFailureException e) {
            // shouldnt happen on insert, but keep the mapping consistent
            throw new ConflictException("subscription write failed due to version conflict", e);
        }

        auditService.record(
                saved.getId(),
                SubscriptionEventType.CREATED,
                null,
                tier.getId(),
                "initial subscription, plan: " + planCode + ", tier: " + tierCode,
                null
        );
        log.info("[sub_lifecycle] subscribed user id: {}, subscription id: {}, plan code: {}, tier code: {}, price: {}",
                userId, saved.getId(), planCode, tierCode, price);
        return saved;
    }

    private Subscription doChangeTier(Long subscriptionId, String newTierCode) {
        Subscription subscription = findByIdOrThrow(subscriptionId);

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.PENDING_DOWNGRADE) {
            throw new ConflictException(
                    "tier change not allowed in status: " + subscription.getStatus());
        }

        MembershipTier currentPurchased = tierRepository.findById(subscription.getPurchasedTierId())
                .orElseThrow(() -> new NotFoundException("current tier not found id: " + subscription.getPurchasedTierId()));
        MembershipTier newTier = tierRepository.findByCode(newTierCode)
                .orElseThrow(() -> new NotFoundException("tier not found code: " + newTierCode));
        if (!Boolean.TRUE.equals(newTier.getActive())) {
            throw new ValidationException("tier inactive code: " + newTierCode);
        }
        if (currentPurchased.getId().equals(newTier.getId())) {
            throw new ValidationException("new tier same as current purchased tier code: " + newTierCode);
        }

        boolean upgrade = newTier.getRank() > currentPurchased.getRank();
        return upgrade
                ? applyUpgrade(subscription, currentPurchased, newTier)
                : applyDowngrade(subscription, currentPurchased, newTier);
    }

    private Subscription applyUpgrade(Subscription subscription, MembershipTier current, MembershipTier newTier) {
        // immediate, prorated charge for the remaining days at the price delta
        MembershipPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new NotFoundException("plan not found id: " + subscription.getPlanId()));

        BigDecimal oldPrice = plan.getBasePrice().multiply(current.getPriceMultiplier());
        BigDecimal newPrice = plan.getBasePrice().multiply(newTier.getPriceMultiplier());
        BigDecimal prorated = prorationCalculator.upgradeProration(
                oldPrice, newPrice, Instant.now(), subscription.getStartDate(), subscription.getEndDate());

        // before mutation, remember from-tier for audit
        Long fromTierId = subscription.getEffectiveTierId();

        subscription.setPurchasedTierId(newTier.getId());
        // upgrades raise the floor — effective moves up too
        subscription.setEffectiveTierId(newTier.getId());
        // an upgrade clears any pending downgrade and reactivates the subscription
        subscription.setScheduledTierId(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPricePaid(subscription.getPricePaid().add(prorated));
        subscription.setUpdatedAt(Instant.now());

        Subscription saved = saveWithVersionCheck(subscription);
        auditService.record(
                saved.getId(),
                SubscriptionEventType.TIER_UPGRADED,
                fromTierId,
                newTier.getId(),
                "tier upgrade applied immediately, prorated charge: " + prorated,
                null
        );
        log.info("[sub_lifecycle] upgraded subscription id: {}, from tier id: {} to tier id: {}, prorated: {}",
                saved.getId(), fromTierId, newTier.getId(), prorated);
        return saved;
    }

    private Subscription applyDowngrade(Subscription subscription, MembershipTier current, MembershipTier newTier) {
        stateMachine.assertCanTransition(subscription.getStatus(), SubscriptionStatus.PENDING_DOWNGRADE);

        // downgrades are scheduled — they take effect at period end via a separate flow
        Long fromTierId = subscription.getPurchasedTierId();
        subscription.setScheduledTierId(newTier.getId());
        subscription.setStatus(SubscriptionStatus.PENDING_DOWNGRADE);
        subscription.setUpdatedAt(Instant.now());

        Subscription saved = saveWithVersionCheck(subscription);
        auditService.record(
                saved.getId(),
                SubscriptionEventType.TIER_DOWNGRADED,
                fromTierId,
                newTier.getId(),
                "downgrade scheduled at period end: " + subscription.getEndDate(),
                null
        );
        log.info("[sub_lifecycle] scheduled downgrade subscription id: {}, from tier id: {} to tier id: {} at end: {}",
                saved.getId(), fromTierId, newTier.getId(), subscription.getEndDate());
        return saved;
    }

    private Subscription doCancel(Long subscriptionId) {
        Subscription subscription = findByIdOrThrow(subscriptionId);
        stateMachine.assertCanTransition(subscription.getStatus(), SubscriptionStatus.CANCELLED_AT_PERIOD_END);

        subscription.setStatus(SubscriptionStatus.CANCELLED_AT_PERIOD_END);
        subscription.setAutoRenew(false);
        subscription.setUpdatedAt(Instant.now());

        Subscription saved = saveWithVersionCheck(subscription);
        auditService.record(
                saved.getId(),
                SubscriptionEventType.CANCELLED,
                null,
                null,
                "cancellation scheduled at period end: " + subscription.getEndDate(),
                null
        );
        log.info("[sub_lifecycle] cancelled subscription id: {} at period end: {}", saved.getId(), subscription.getEndDate());
        return saved;
    }

    // map jpa optimistic lock failures to our domain exception so callers
    // (and the global handler) see a clean 409
    private Subscription saveWithVersionCheck(Subscription subscription) {
        try {
            return subscriptionRepository.save(subscription);
        } catch (OptimisticLockingFailureException e) {
            log.warn("[sub_lifecycle] optimistic lock conflict on subscription id: {}, version: {}",
                    subscription.getId(), subscription.getVersion());
            throw new ConflictException(
                    "subscription was modified concurrently, retry the operation", e);
        }
    }
}
