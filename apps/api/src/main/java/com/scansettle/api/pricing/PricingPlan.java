package com.scansettle.api.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pricing_plan")
public class PricingPlan {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true)
    private PlanCode code;

    /** e.g. 0.0035 for 0.35% — stored as a fraction, not a percentage, to avoid ambiguity. */
    @Column(name = "fee_fraction", nullable = false)
    private BigDecimal feeFraction;

    @Column(name = "fee_cap_minor_units", nullable = false)
    private long feeCapMinorUnits;

    @Column(name = "monthly_subscription_minor_units", nullable = false)
    private long monthlySubscriptionMinorUnits;

    @Column(nullable = false)
    private boolean active;

    protected PricingPlan() {
        // JPA
    }

    public PricingPlan(UUID id, PlanCode code, BigDecimal feeFraction, long feeCapMinorUnits,
                        long monthlySubscriptionMinorUnits, boolean active) {
        this.id = id;
        this.code = code;
        this.feeFraction = feeFraction;
        this.feeCapMinorUnits = feeCapMinorUnits;
        this.monthlySubscriptionMinorUnits = monthlySubscriptionMinorUnits;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public PlanCode getCode() {
        return code;
    }

    public BigDecimal getFeeFraction() {
        return feeFraction;
    }

    public long getFeeCapMinorUnits() {
        return feeCapMinorUnits;
    }

    public long getMonthlySubscriptionMinorUnits() {
        return monthlySubscriptionMinorUnits;
    }

    public boolean isActive() {
        return active;
    }
}
