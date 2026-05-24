package com.work.membership_service.repository;

import com.work.membership_service.model.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    // count of orders for a user within a rolling window
    @Query("select count(o) from OrderEntity o where o.userId = :userId and o.placedAt >= :since")
    long countByUserIdAndPlacedAtAfter(@Param("userId") Long userId, @Param("since") Instant since);

    // sum of order amounts for a user within a rolling window; coalesce to zero if empty
    @Query("select coalesce(sum(o.amount), 0) from OrderEntity o " +
            "where o.userId = :userId and o.placedAt >= :since")
    BigDecimal sumAmountByUserIdAndPlacedAtAfter(@Param("userId") Long userId, @Param("since") Instant since);

    // lifetime sum
    @Query("select coalesce(sum(o.amount), 0) from OrderEntity o where o.userId = :userId")
    BigDecimal sumAmountByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
