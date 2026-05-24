package com.work.membership_service.engine.benefit;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.constant.enums.BenefitType;
import com.work.membership_service.engine.benefit.impl.EarlyAccessBenefit;
import com.work.membership_service.engine.benefit.impl.ExclusiveDealsBenefit;
import com.work.membership_service.engine.benefit.impl.ExtraDiscountBenefit;
import com.work.membership_service.engine.benefit.impl.FreeDeliveryBenefit;
import com.work.membership_service.engine.benefit.impl.PrioritySupportBenefit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// turns a json array of {type, params} into ready-to-apply Benefit instances
@Component
@Slf4j
public class BenefitFactory {

    // build a list of Benefit from the json stored in benefit_config.benefits
    public List<Benefit> build(JsonNode benefitsArray) {
        if (benefitsArray == null || !benefitsArray.isArray()) {
            return List.of();
        }
        List<Benefit> out = new ArrayList<>();
        for (JsonNode node : benefitsArray) {
            out.add(buildOne(node));
        }
        return out;
    }

    // validate a json array at admin-create time so bad config never reaches the cache
    public void validate(JsonNode benefitsArray) {
        if (benefitsArray == null || !benefitsArray.isArray()) {
            throw new IllegalArgumentException("benefits must be a json array");
        }
        for (JsonNode node : benefitsArray) {
            buildOne(node); // build is the validation — exceptions propagate
        }
    }

    private Benefit buildOne(JsonNode node) {
        if (!node.has("type") || !node.has("params")) {
            throw new IllegalArgumentException("benefit entry must have {type, params}");
        }
        BenefitType type;
        try {
            type = BenefitType.valueOf(node.get("type").asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown benefit type: " + node.get("type").asText());
        }
        JsonNode params = node.get("params");
        return switch (type) {
            case FREE_DELIVERY -> {
                require(params, "minOrderValue");
                yield new FreeDeliveryBenefit(new BigDecimal(params.get("minOrderValue").asText()));
            }
            case EXTRA_DISCOUNT -> {
                require(params, "percent");
                require(params, "categories");
                BigDecimal percent = new BigDecimal(params.get("percent").asText());
                if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("EXTRA_DISCOUNT percent must be in [0, 100]");
                }
                Set<String> cats = new HashSet<>();
                params.get("categories").forEach(n -> cats.add(n.asText()));
                yield new ExtraDiscountBenefit(percent, cats);
            }
            case EXCLUSIVE_DEALS -> {
                require(params, "dealIds");
                List<String> ids = new ArrayList<>();
                params.get("dealIds").forEach(n -> ids.add(n.asText()));
                yield new ExclusiveDealsBenefit(ids);
            }
            case EARLY_ACCESS -> {
                require(params, "hoursEarly");
                int hours = params.get("hoursEarly").asInt();
                if (hours <= 0) {
                    throw new IllegalArgumentException("EARLY_ACCESS hoursEarly must be > 0");
                }
                yield new EarlyAccessBenefit(hours);
            }
            case PRIORITY_SUPPORT -> {
                require(params, "slaMinutes");
                int sla = params.get("slaMinutes").asInt();
                if (sla <= 0) {
                    throw new IllegalArgumentException("PRIORITY_SUPPORT slaMinutes must be > 0");
                }
                yield new PrioritySupportBenefit(sla);
            }
        };
    }

    private static void require(JsonNode params, String field) {
        if (params == null || !params.has(field)) {
            throw new IllegalArgumentException("missing param: " + field);
        }
    }
}
