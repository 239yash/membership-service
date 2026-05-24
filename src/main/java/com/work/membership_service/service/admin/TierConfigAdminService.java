package com.work.membership_service.service.admin;

import com.work.membership_service.engine.benefit.BenefitFactory;
import com.work.membership_service.engine.criterion.CriterionEngine;
import com.work.membership_service.event.TierConfigChangedEvent;
import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.model.entity.BenefitConfig;
import com.work.membership_service.model.entity.CriterionRule;
import com.work.membership_service.model.entity.MembershipTier;
import com.work.membership_service.repository.BenefitConfigRepository;
import com.work.membership_service.repository.CriterionRuleRepository;
import com.work.membership_service.repository.MembershipTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

// admin writes for criterion rules and benefit configs
// create operations validate the json against the relevant engine before saving
// activate operations flip the tier's fk pointer and publish TierConfigChangedEvent (cache invalidation)
@Service
@RequiredArgsConstructor
@Slf4j
public class TierConfigAdminService {

    private final CriterionRuleRepository criterionRuleRepository;
    private final BenefitConfigRepository benefitConfigRepository;
    private final MembershipTierRepository tierRepository;
    private final CriterionEngine criterionEngine;
    private final BenefitFactory benefitFactory;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CriterionRule createCriterion(JsonNode ruleTree, String description, String createdBy) {
        // fail fast if the tree is malformed or references unknown criterion types
        criterionEngine.validate(ruleTree);

        CriterionRule row = CriterionRule.builder()
                .ruleTree(toJsonString(ruleTree))
                .description(description)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build();
        CriterionRule saved = criterionRuleRepository.save(row);
        log.info("[admin_config] created criterion rule id: {}, createdBy: {}", saved.getId(), createdBy);
        return saved;
    }

    @Transactional
    public BenefitConfig createBenefitConfig(JsonNode benefitsArray, String description, String createdBy) {
        // fail fast if any benefit entry is unknown or has bad params
        benefitFactory.validate(benefitsArray);

        BenefitConfig row = BenefitConfig.builder()
                .benefits(toJsonString(benefitsArray))
                .description(description)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build();
        BenefitConfig saved = benefitConfigRepository.save(row);
        log.info("[admin_config] created benefit config id: {}, createdBy: {}", saved.getId(), createdBy);
        return saved;
    }

    @Transactional
    public ActivationResult activateCriteriaOn(String tierCode, Long criterionRuleId) {
        MembershipTier tier = tierRepository.findByCode(tierCode)
                .orElseThrow(() -> new NotFoundException("tier not found code: " + tierCode));
        criterionRuleRepository.findById(criterionRuleId)
                .orElseThrow(() -> new NotFoundException("criterion rule not found id: " + criterionRuleId));

        Long previous = tier.getActiveCriterionRuleId();
        tier.setActiveCriterionRuleId(criterionRuleId);
        tier.setUpdatedAt(Instant.now());
        tierRepository.save(tier);

        // delivered AFTER_COMMIT to the cache invalidator
        eventPublisher.publishEvent(new TierConfigChangedEvent(tierCode));
        log.info("[admin_config] activated criterion tier code: {}, prev id: {}, new id: {}",
                tierCode, previous, criterionRuleId);
        return new ActivationResult(tierCode, "CRITERIA", previous, criterionRuleId);
    }

    @Transactional
    public ActivationResult activateBenefitsOn(String tierCode, Long benefitConfigId) {
        MembershipTier tier = tierRepository.findByCode(tierCode)
                .orElseThrow(() -> new NotFoundException("tier not found code: " + tierCode));
        benefitConfigRepository.findById(benefitConfigId)
                .orElseThrow(() -> new NotFoundException("benefit config not found id: " + benefitConfigId));

        Long previous = tier.getActiveBenefitConfigId();
        tier.setActiveBenefitConfigId(benefitConfigId);
        tier.setUpdatedAt(Instant.now());
        tierRepository.save(tier);

        eventPublisher.publishEvent(new TierConfigChangedEvent(tierCode));
        log.info("[admin_config] activated benefits tier code: {}, prev id: {}, new id: {}",
                tierCode, previous, benefitConfigId);
        return new ActivationResult(tierCode, "BENEFITS", previous, benefitConfigId);
    }

    public record ActivationResult(
            String tierCode,
            String configType,
            Long previousVersionId,
            Long activeVersionId
    ) {
    }

    private String toJsonString(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialize json", e);
        }
    }
}
