package com.work.membership_service.engine.benefit;

import com.work.membership_service.constant.enums.BenefitType;

// one benefit instance bound to a specific set of params
// not a spring bean — built by BenefitFactory from json config per tier
public interface Benefit {

    BenefitType type();

    BenefitOutcome apply(CartContext context);
}
