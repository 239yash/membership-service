package com.work.membership_service.service.tier;

import com.work.membership_service.event.TierConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// drops the redis entry for a tier whenever its active rule or benefit pointer flips
// AFTER_COMMIT keeps us safe against rollback — never invalidate on a transaction that didnt land
@Component
@RequiredArgsConstructor
@Slf4j
public class TierConfigCacheInvalidator {

    private final TierConfigService tierConfigService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(TierConfigChangedEvent event) {
        log.info("[cache] invalidating tier config code: {}", event.tierCode());
        tierConfigService.invalidate(event.tierCode());
    }
}
