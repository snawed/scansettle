package com.scansettle.api.pricing;

import com.scansettle.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPlanRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PricingPlanRepository pricingPlanRepository;

    @Test
    void basicPlanIsSeededByFlywayMigration() {
        PricingPlan basic = pricingPlanRepository.findByCode(PlanCode.BASIC).orElseThrow();

        assertThat(basic.getFeeFraction()).isEqualByComparingTo(new BigDecimal("0.0035"));
        assertThat(basic.getFeeCapMinorUnits()).isEqualTo(200L); // £2.00 cap
        assertThat(basic.getMonthlySubscriptionMinorUnits()).isEqualTo(0L);
        assertThat(basic.isActive()).isTrue();
    }
}
