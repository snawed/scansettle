package com.scansettle.api.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per terminal payment outcome, comparing what ScanSettle expected against
 * what the provider actually confirmed (docs/architecture.md — reconciliation
 * foundation). The mock provider never reports a different amount than requested, so
 * every record from it is {@code matched=true}; a real provider that can report
 * partial/short settlement would start populating genuine mismatches here without
 * any schema change.
 */
@Entity
@Table(name = "reconciliation_record")
public class ReconciliationRecord {

    @Id
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "provider_transaction_id")
    private UUID providerTransactionId;

    @Column(name = "expected_amount_minor_units", nullable = false)
    private long expectedAmountMinorUnits;

    @Column(name = "confirmed_amount_minor_units")
    private Long confirmedAmountMinorUnits;

    @Column(nullable = false)
    private boolean matched;

    @Column(name = "discrepancy_note")
    private String discrepancyNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReconciliationRecord() {
        // JPA
    }

    public ReconciliationRecord(UUID id, UUID paymentId, UUID providerTransactionId, long expectedAmountMinorUnits,
                                 Long confirmedAmountMinorUnits, String discrepancyNote) {
        this.id = id;
        this.paymentId = paymentId;
        this.providerTransactionId = providerTransactionId;
        this.expectedAmountMinorUnits = expectedAmountMinorUnits;
        this.confirmedAmountMinorUnits = confirmedAmountMinorUnits;
        this.matched = confirmedAmountMinorUnits != null && confirmedAmountMinorUnits == expectedAmountMinorUnits;
        this.discrepancyNote = discrepancyNote;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getProviderTransactionId() {
        return providerTransactionId;
    }

    public long getExpectedAmountMinorUnits() {
        return expectedAmountMinorUnits;
    }

    public Long getConfirmedAmountMinorUnits() {
        return confirmedAmountMinorUnits;
    }

    public boolean isMatched() {
        return matched;
    }

    public String getDiscrepancyNote() {
        return discrepancyNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
