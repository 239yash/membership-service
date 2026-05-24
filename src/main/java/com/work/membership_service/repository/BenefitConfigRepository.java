package com.work.membership_service.repository;

import com.work.membership_service.model.entity.BenefitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BenefitConfigRepository extends JpaRepository<BenefitConfig, Long> {
}
