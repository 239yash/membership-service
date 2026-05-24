package com.work.membership_service.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// tier metadata + forward fk pointers to the currently active rule and benefits
@Entity
@Table(name = "membership_tier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    // higher rank = higher tier
    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "price_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal priceMultiplier;

    @Column(nullable = false)
    private Boolean active;

    // points at the currently active criterion_rule row
    @Column(name = "active_criterion_rule_id")
    private Long activeCriterionRuleId;

    // points at the currently active benefit_config row
    @Column(name = "active_benefit_config_id")
    private Long activeBenefitConfigId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
