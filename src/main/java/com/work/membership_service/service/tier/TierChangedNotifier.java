package com.work.membership_service.service.tier;

import com.work.membership_service.event.TierChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// placeholder downstream for tier changes — a real system would push a notification,
// fire a coupon grant, or update analytics. for now we just log.
@Component
@Slf4j
public class TierChangedNotifier {

    @EventListener
    public void onTierChanged(TierChangedEvent event) {
        log.info("[tier_change] notify user id: {}, sub id: {}, from tier id: {} -> to tier id: {}, kind: {}",
                event.userId(),
                event.subscriptionId(),
                event.fromTierId(),
                event.toTierId(),
                event.kind());
    }
}
