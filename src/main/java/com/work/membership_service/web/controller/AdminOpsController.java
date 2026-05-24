package com.work.membership_service.web.controller;

import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.service.tier.TierEvaluationService;
import com.work.membership_service.web.dto.response.SweepResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminOpsController {

    private final TierEvaluationService tierEvaluationService;

    @PostMapping("/tier-sweep")
    public ApiResponse<SweepResponse> runSweep() {
        log.info("[tier_sweep] triggered by admin");
        long t0 = System.currentTimeMillis();
        TierEvaluationService.SweepResult r = tierEvaluationService.runSweep();
        long elapsed = System.currentTimeMillis() - t0;
        return ApiResponse.ok(new SweepResponse(r.evaluated(), r.tierChanges(), elapsed));
    }
}
