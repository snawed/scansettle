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
 * The mechanism that actually enforces "no overpayment" under concurrent payers
 * (docs/scansettle-tables.md, ADR-0003) — created inside a row-locked transaction on
 * {@link Bill} *before* any bank redirect, so a request that doesn't fit is rejected
 * immediately rather than after a wasted trip to the bank.
 */
@Entity
@Table(name = "bill_payment_reservation")
public class BillPaymentReservation {

    public enum Status {
        ACTIVE, COMMITTED, RELEASED, EXPIRED
    }

    @Id
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "requested_amount_minor_units", nullable = false)
    private long requestedAmountMinorUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "bill_payment_id")
    private UUID billPaymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BillPaymentReservation() {
        // JPA
    }

    public BillPaymentReservation(UUID id, UUID billId, long requestedAmountMinorUnits, Instant expiresAt,
                                   UUID billPaymentId) {
        this.id = id;
        this.billId = billId;
        this.requestedAmountMinorUnits = requestedAmountMinorUnits;
        this.status = Status.ACTIVE;
        this.expiresAt = expiresAt;
        this.billPaymentId = billPaymentId;
        this.createdAt = Instant.now();
    }

    public void commit() {
        this.status = Status.COMMITTED;
    }

    public void release() {
        this.status = Status.RELEASED;
    }

    public void expire() {
        this.status = Status.EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBillId() {
        return billId;
    }

    public long getRequestedAmountMinorUnits() {
        return requestedAmountMinorUnits;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getBillPaymentId() {
        return billPaymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
