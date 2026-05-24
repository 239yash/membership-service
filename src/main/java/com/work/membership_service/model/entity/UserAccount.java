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

// a registered user, optionally tagged with cohorts (used by criteria)
@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 128)
    private String name;

    @Column(length = 128, unique = true)
    private String email;

    // postgres text[] of cohort tags
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cohorts", nullable = false, columnDefinition = "text[]")
    private String[] cohorts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
