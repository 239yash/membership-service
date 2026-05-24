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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// a versioned, tier-agnostic eligibility rule tree
// id is the version, rows are immutable once written
@Entity
@Table(name = "criterion_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriterionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // raw json, parsed by the engine at evaluation time
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_tree", nullable = false, columnDefinition = "jsonb")
    private String ruleTree;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
