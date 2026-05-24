package com.work.membership_service.web.controller;

import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.model.entity.BenefitConfig;
import com.work.membership_service.model.entity.CriterionRule;
import com.work.membership_service.service.admin.TierConfigAdminService;
import com.work.membership_service.web.dto.request.ActivateBenefitsRequest;
import com.work.membership_service.web.dto.request.ActivateCriterionRequest;
import com.work.membership_service.web.dto.request.CreateBenefitConfigRequest;
import com.work.membership_service.web.dto.request.CreateCriterionRequest;
import com.work.membership_service.web.dto.response.ActivationResponse;
import com.work.membership_service.web.dto.response.BenefitConfigCreatedResponse;
import com.work.membership_service.web.dto.response.CriterionCreatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminConfigController {

    private final TierConfigAdminService tierConfigAdminService;

    @PostMapping("/criteria")
    public ResponseEntity<ApiResponse<CriterionCreatedResponse>> createCriterion(
            @Valid @RequestBody CreateCriterionRequest request) {
        log.info("[admin_config] create criterion description: {}", request.description());
        CriterionRule createdRule = tierConfigAdminService.createCriterion(
                request.ruleTree(), request.description(), request.createdBy());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new CriterionCreatedResponse(
                        createdRule.getId(),
                        createdRule.getDescription(),
                        createdRule.getCreatedBy(),
                        createdRule.getCreatedAt())));
    }

    @PostMapping("/benefits")
    public ResponseEntity<ApiResponse<BenefitConfigCreatedResponse>> createBenefits(
            @Valid @RequestBody CreateBenefitConfigRequest request) {
        log.info("[admin_config] create benefit config description: {}", request.description());
        BenefitConfig createdConfig = tierConfigAdminService.createBenefitConfig(
                request.benefits(), request.description(), request.createdBy());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new BenefitConfigCreatedResponse(
                        createdConfig.getId(),
                        createdConfig.getDescription(),
                        createdConfig.getCreatedBy(),
                        createdConfig.getCreatedAt())));
    }

    @PostMapping("/tiers/{tierCode}/activate-criteria")
    public ApiResponse<ActivationResponse> activateCriteria(
            @PathVariable String tierCode,
            @Valid @RequestBody ActivateCriterionRequest request) {
        log.info("[admin_config] activate criteria tier: {}, criterion rule id: {}",
                tierCode, request.criterionRuleId());
        TierConfigAdminService.ActivationResult activation =
                tierConfigAdminService.activateCriteriaOn(tierCode, request.criterionRuleId());
        return ApiResponse.ok(new ActivationResponse(
                activation.tierCode(),
                activation.configType(),
                activation.previousVersionId(),
                activation.activeVersionId(),
                Instant.now()));
    }

    @PostMapping("/tiers/{tierCode}/activate-benefits")
    public ApiResponse<ActivationResponse> activateBenefits(
            @PathVariable String tierCode,
            @Valid @RequestBody ActivateBenefitsRequest request) {
        log.info("[admin_config] activate benefits tier: {}, benefit config id: {}",
                tierCode, request.benefitConfigId());
        TierConfigAdminService.ActivationResult activation =
                tierConfigAdminService.activateBenefitsOn(tierCode, request.benefitConfigId());
        return ApiResponse.ok(new ActivationResponse(
                activation.tierCode(),
                activation.configType(),
                activation.previousVersionId(),
                activation.activeVersionId(),
                Instant.now()));
    }
}
