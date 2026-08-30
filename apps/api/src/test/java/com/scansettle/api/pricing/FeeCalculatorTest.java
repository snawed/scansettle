package com.scansettle.api.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeCalculator feeCalculator = new FeeCalculator();

    private PricingPlan basicPlan() {
        return new PricingPlan(UUID.randomUUID(), PlanCode.BASIC, new BigDecimal("0.0035"), 200, 0, true);
    }

    @Test
    void feeIsPercentageBelowTheCap() {
        // £100.00 (10000p) * 0.35% = 35p, well under the £2.00 cap.
        assertThat(feeCalculator.calculateFeeMinorUnits(10000, basicPlan())).isEqualTo(35L);
    }

    @Test
    void feeIsCappedForLargePayments() {
        // £2,500.00 (250000p) * 0.35% = 875p (£8.75) — must be capped at 200p (£2.00).
        assertThat(feeCalculator.calculateFeeMinorUnits(250000, basicPlan())).isEqualTo(200L);
    }

    @Test
    void feeExactlyAtCapBoundaryIsNotOvercapped() {
        // 200p / 0.0035 ≈ 57142.86 -> at 57143p the raw fee is 200.0005p, rounds to 200p either way.
        assertThat(feeCalculator.calculateFeeMinorUnits(57142, basicPlan())).isLessThanOrEqualTo(200L);
    }

    @Test
    void zeroFractionPlanChargesNoFee() {
        PricingPlan freePlan = new PricingPlan(UUID.randomUUID(), PlanCode.FREE, BigDecimal.ZERO, 0, 0, true);
        assertThat(feeCalculator.calculateFeeMinorUnits(10000, freePlan)).isEqualTo(0L);
    }
}
