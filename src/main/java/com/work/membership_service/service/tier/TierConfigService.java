package com.work.membership_service.service.tier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.work.membership_service.model.entity.BenefitConfig;
import com.work.membership_service.model.entity.CriterionRule;
import com.work.membership_service.model.entity.MembershipTier;
import com.work.membership_service.repository.BenefitConfigRepository;
import com.work.membership_service.repository.CriterionRuleRepository;
import com.work.membership_service.repository.MembershipTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// the read path for a tier's full config (metadata + active rule + active benefits)
// cache-aside on top of redis: try the cache, on miss load from postgres and repopulate
// invalidate() is called by the admin-activate flow when a tier's fk pointer is flipped
@Service
@RequiredArgsConstructor
@Slf4j
public class TierConfigService {

    private static final String KEY_PREFIX = "tier:config:";

    private final MembershipTierRepository tierRepository;
    private final CriterionRuleRepository criterionRuleRepository;
    private final BenefitConfigRepository benefitConfigRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // load one tier config by code; cache miss falls back to postgres
    public TierConfig getByCode(String tierCode) {
        String key = KEY_PREFIX + tierCode;

        // try the cache first
        String cached = safeGet(key);
        if (cached != null) {
            try {
                log.debug("[tier_config] cache hit code: {}", tierCode);
                return objectMapper.readValue(cached, TierConfig.class);
            } catch (JsonProcessingException e) {
                log.warn("[tier_config] cached value unparseable code: {}, dropping", tierCode);
                redis.delete(key);
            }
        }

        // cache miss — load from db
        log.info("[tier_config] cache miss code: {}, loading from db", tierCode);
        TierConfig tc = loadFromDb(tierCode);

        // best-effort write back; failures shouldnt break the request
        safeSet(key, tc);
        return tc;
    }

    // load all active tiers in rank desc order; primarily for tier evaluation
    public List<TierConfig> getAllActive() {
        List<MembershipTier> tiers = tierRepository.findAllByActiveTrueOrderByRankDesc();
        List<TierConfig> out = new ArrayList<>(tiers.size());
        for (MembershipTier tier : tiers) {
            out.add(getByCode(tier.getCode()));
        }
        return out;
    }

    // drop the cache entry for one tier — called when admin activates a new version
    public void invalidate(String tierCode) {
        String key = KEY_PREFIX + tierCode;
        Boolean removed = redis.delete(key);
        log.info("[tier_config] invalidated cache code: {}, removed: {}", tierCode, removed);
    }

    private TierConfig loadFromDb(String tierCode) {
        MembershipTier tier = tierRepository.findByCode(tierCode)
                .orElseThrow(() -> new IllegalArgumentException("tier not found code: " + tierCode));

        JsonNode ruleTree = emptyAndNode();
        Long ruleId = tier.getActiveCriterionRuleId();
        if (ruleId != null) {
            CriterionRule rule = criterionRuleRepository.findById(ruleId)
                    .orElseThrow(() -> new IllegalStateException(
                            "active criterion rule missing for tier " + tierCode + " id: " + ruleId));
            ruleTree = parse(rule.getRuleTree(), "criterion_rule id: " + ruleId);
        } else {
            log.warn("[tier_config] tier code: {} has no active criterion rule, defaulting to empty AND", tierCode);
        }

        JsonNode benefits = objectMapper.createArrayNode();
        Long benefitId = tier.getActiveBenefitConfigId();
        if (benefitId != null) {
            BenefitConfig bc = benefitConfigRepository.findById(benefitId)
                    .orElseThrow(() -> new IllegalStateException(
                            "active benefit config missing for tier " + tierCode + " id: " + benefitId));
            benefits = parse(bc.getBenefits(), "benefit_config id: " + benefitId);
        } else {
            log.warn("[tier_config] tier code: {} has no active benefit config, defaulting to empty array", tierCode);
        }

        return new TierConfig(
                tier.getId(),
                tier.getCode(),
                tier.getName(),
                tier.getRank(),
                tier.getPriceMultiplier(),
                ruleId,
                ruleTree,
                benefitId,
                benefits
        );
    }

    private JsonNode parse(String raw, String label) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("malformed json in " + label, e);
        }
    }

    private JsonNode emptyAndNode() {
        try {
            return objectMapper.readTree("{\"op\":\"AND\",\"children\":[]}");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not build empty rule node", e);
        }
    }

    // wrap redis reads so a transient cache outage just becomes a db hit
    private String safeGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[tier_config] cache read failed key: {}, err: {}", key, e.getMessage());
            return null;
        }
    }

    private void safeSet(String key, TierConfig tc) {
        try {
            String json = objectMapper.writeValueAsString(tc);
            redis.opsForValue().set(key, json);
            log.debug("[tier_config] cached code: {}", tc.tierCode());
        } catch (Exception e) {
            log.warn("[tier_config] cache write failed key: {}, err: {}", key, e.getMessage());
        }
    }
}
