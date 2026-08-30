package com.scansettle.api.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One customer's contribution to a {@link Bill} — tip is a field here, not a
 * separate entity (docs/domain-model.md): it always has the same lifecycle as the
 * contribution it rides alongside in a single bank authorisation.
 */
@Entity
@Table(name = "bill_payment")
public class BillPayment {

    public enum TipMethod {
        NONE, PERCENT_5, PERCENT_10, CUSTOM
    }

    public enum Status {
        CREATED, CONFIRMED, FAILED
    }

    @Id
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "contribution_amount_minor_units", nullable = false)
    private long contributionAmountMinorUnits;

    @Column(name = "tip_amount_minor_units", nullable = false)
    private long tipAmountMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip_method", nullable = false)
    private TipMethod tipMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status state;

    @Column(name = "payer_contact")
    private String payerContact;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BillPayment() {
        // JPA
    }

    public BillPayment(UUID id, UUID billId, long contributionAmountMinorUnits, long tipAmountMinorUnits,
                        TipMethod tipMethod, String payerContact) {
        this.id = id;
        this.billId = billId;
        this.contributionAmountMinorUnits = contributionAmountMinorUnits;
        this.tipAmountMinorUnits = tipAmountMinorUnits;
        this.tipMethod = tipMethod;
        this.state = Status.CREATED;
        this.payerContact = payerContact;
        this.createdAt = Instant.now();
    }

    public long totalAmountMinorUnits() {
        return contributionAmountMinorUnits + tipAmountMinorUnits;
    }

    public void markConfirmed() {
        this.state = Status.CONFIRMED;
    }

    public void markFailed() {
        this.state = Status.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBillId() {
        return billId;
    }

    public long getContributionAmountMinorUnits() {
        return contributionAmountMinorUnits;
    }

    public long getTipAmountMinorUnits() {
        return tipAmountMinorUnits;
    }

    public TipMethod getTipMethod() {
        return tipMethod;
    }

    public Status getState() {
        return state;
    }

    public String getPayerContact() {
        return payerContact;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
