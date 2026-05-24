package com.work.membership_service.repository;

import com.work.membership_service.model.entity.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    List<SubscriptionEvent> findAllBySubscriptionIdOrderByOccurredAtDesc(Long subscriptionId);
}
