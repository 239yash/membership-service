package com.work.membership_service.service.tier;

import com.work.membership_service.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// hands an OrderPlacedEvent off to the tier evaluator
// AFTER_COMMIT: only react to durable orders, never to one that rolls back
// runs synchronously on the request thread — caller sees an order response only after eval finishes
@Component
@RequiredArgsConstructor
@Slf4j
public class TierEvaluationListener {

    private final TierEvaluationService tierEvaluationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.debug("[tier_eval] received order placed user id: {}, order id: {}",
                event.userId(), event.orderId());
        try {
            tierEvaluationService.evaluate(event.userId());
        } catch (Exception e) {
            // last-resort guard — listener exceptions would otherwise bubble out of the request
            log.error("[tier_eval] unhandled error user id: {}, err: {}",
                    event.userId(), e.getMessage(), e);
        }
    }
}
