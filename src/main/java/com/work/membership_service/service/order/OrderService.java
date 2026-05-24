package com.work.membership_service.service.order;

import com.work.membership_service.event.OrderPlacedEvent;
import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.exception.ValidationException;
import com.work.membership_service.model.entity.OrderEntity;
import com.work.membership_service.repository.OrderRepository;
import com.work.membership_service.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

// minimal order placement — persist + publish event
// the tier eval listener consumes the event AFTER_COMMIT
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserAccountRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderEntity placeOrder(Long userId, BigDecimal amount, String category) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user not found id: " + userId));
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("order amount must be > 0");
        }

        OrderEntity order = OrderEntity.builder()
                .userId(userId)
                .amount(amount)
                .category(category)
                .placedAt(Instant.now())
                .build();
        OrderEntity saved = orderRepository.save(order);

        log.info("[order_flow] placed order id: {}, user id: {}, amount: {}, category: {}",
                saved.getId(), userId, amount, category);

        // event delivery is deferred to AFTER_COMMIT by the listener
        eventPublisher.publishEvent(new OrderPlacedEvent(
                saved.getId(), userId, amount, category, saved.getPlacedAt()));
        return saved;
    }
}
