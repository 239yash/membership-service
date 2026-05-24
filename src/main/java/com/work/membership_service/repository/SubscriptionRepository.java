package com.work.membership_service.repository;

import com.work.membership_service.constant.enums.SubscriptionStatus;
import com.work.membership_service.model.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // there can be only one live subscription per user (enforced by db partial unique index)
    Optional<Subscription> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            Long userId, Collection<SubscriptionStatus> statuses);

    // newest first
    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    // used by the tier sweep
    List<Subscription> findAllByStatusIn(Collection<SubscriptionStatus> statuses);
}
