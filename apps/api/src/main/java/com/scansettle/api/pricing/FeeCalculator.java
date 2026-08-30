package com.scansettle.api.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** docs/architecture.md Section 11 — fee = min(amount * feeFraction, feeCap), rounded to the nearest penny. */
@Component
public class FeeCalculator {

    public long calculateFeeMinorUnits(long amountMinorUnits, PricingPlan plan) {
        BigDecimal rawFee = BigDecimal.valueOf(amountMinorUnits).multiply(plan.getFeeFraction());
        long feeMinorUnits = rawFee.setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Math.min(feeMinorUnits, plan.getFeeCapMinorUnits());
    }
}
