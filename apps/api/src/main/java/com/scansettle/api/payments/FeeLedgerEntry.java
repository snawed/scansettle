package com.scansettle.api.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One row per successful payment — docs/architecture.md Section 11. */
@Entity
@Table(name = "fee_ledger_entry")
public class FeeLedgerEntry {

    @Id
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "pricing_plan_id", nullable = false)
    private UUID pricingPlanId;

    @Column(name = "calculated_fee_minor_units", nullable = false)
    private long calculatedFeeMinorUnits;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FeeLedgerEntry() {
        // JPA
    }

    public FeeLedgerEntry(UUID id, UUID paymentId, UUID merchantId, UUID pricingPlanId, long calculatedFeeMinorUnits) {
        this.id = id;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.pricingPlanId = pricingPlanId;
        this.calculatedFeeMinorUnits = calculatedFeeMinorUnits;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getPricingPlanId() {
        return pricingPlanId;
    }

    public long getCalculatedFeeMinorUnits() {
        return calculatedFeeMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
