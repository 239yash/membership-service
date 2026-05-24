package com.work.membership_service.repository;

import com.work.membership_service.model.entity.MembershipTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipTierRepository extends JpaRepository<MembershipTier, Long> {

    Optional<MembershipTier> findByCode(String code);

    // ordered top down so the evaluator can pick the highest matching tier first
    List<MembershipTier> findAllByActiveTrueOrderByRankDesc();
}
