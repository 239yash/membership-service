package com.work.membership_service.web.controller;

import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.model.entity.MembershipPlan;
import com.work.membership_service.repository.MembershipPlanRepository;
import com.work.membership_service.service.tier.TierConfig;
import com.work.membership_service.service.tier.TierConfigService;
import com.work.membership_service.web.dto.response.PlanResponse;
import com.work.membership_service.web.dto.response.TierResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CatalogController {

    private final MembershipPlanRepository planRepository;
    private final TierConfigService tierConfigService;

    @GetMapping("/plans")
    public ApiResponse<List<PlanResponse>> listPlans() {
        log.debug("[catalog] list plans");
        List<PlanResponse> data = planRepository.findAllByActiveTrue().stream()
                .map(this::toPlanResponse)
                .toList();
        return ApiResponse.ok(data);
    }

    @GetMapping("/tiers")
    public ApiResponse<List<TierResponse>> listTiers() {
        log.debug("[catalog] list tiers");
        List<TierResponse> data = tierConfigService.getAllActive().stream()
                .map(this::toTierResponse)
                .toList();
        return ApiResponse.ok(data);
    }

    private PlanResponse toPlanResponse(MembershipPlan plan) {
        return new PlanResponse(
                plan.getId(), plan.getCode(), plan.getName(),
                plan.getBillingFrequency(), plan.getDurationDays(),
                plan.getBasePrice(), plan.getActive());
    }

    private TierResponse toTierResponse(TierConfig tierConfig) {
        return new TierResponse(
                tierConfig.tierId(),
                tierConfig.tierCode(),
                tierConfig.tierName(),
                tierConfig.rank(),
                tierConfig.priceMultiplier(),
                tierConfig.criterionRuleId(),
                tierConfig.ruleTree(),
                tierConfig.benefitConfigId(),
                tierConfig.benefits());
    }
}
