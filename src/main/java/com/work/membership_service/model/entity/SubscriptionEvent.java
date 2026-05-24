package com.work.membership_service.model.entity;

import com.work.membership_service.constant.enums.SubscriptionEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// append-only audit row for any state change on a subscription
@Entity
@Table(name = "subscription_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionEventType type;

    @Column(name = "from_tier_id")
    private Long fromTierId;

    @Column(name = "to_tier_id")
    private Long toTierId;

    @Column(columnDefinition = "text")
    private String reason;

    // freeform json blob, useful for capturing what stats triggered an auto-promotion
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
