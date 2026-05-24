package com.work.membership_service.service.subscription;

import com.work.membership_service.constant.enums.SubscriptionEventType;
import com.work.membership_service.model.entity.SubscriptionEvent;
import com.work.membership_service.repository.SubscriptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

// thin wrapper for appending audit rows
// every state-changing op in SubscriptionService and the tier evaluator goes through this
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAuditService {

    private final SubscriptionEventRepository eventRepository;

    public void record(
            Long subscriptionId,
            SubscriptionEventType type,
            Long fromTierId,
            Long toTierId,
            String reason,
            String metadataJson) {

        SubscriptionEvent event = SubscriptionEvent.builder()
                .subscriptionId(subscriptionId)
                .type(type)
                .fromTierId(fromTierId)
                .toTierId(toTierId)
                .reason(reason)
                .metadata(metadataJson)
                .occurredAt(Instant.now())
                .build();
        eventRepository.save(event);
        log.info("[sub_audit] recorded sub id: {}, type: {}, from tier id: {}, to tier id: {}",
                subscriptionId, type, fromTierId, toTierId);
    }
}
