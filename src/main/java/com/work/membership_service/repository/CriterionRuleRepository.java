package com.work.membership_service.repository;

import com.work.membership_service.model.entity.CriterionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CriterionRuleRepository extends JpaRepository<CriterionRule, Long> {
}
