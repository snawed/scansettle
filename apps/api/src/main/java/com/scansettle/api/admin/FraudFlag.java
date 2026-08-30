package com.scansettle.api.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised against a merchant or a specific payment — exactly one of the two is set.
 * {@code raisedBy} is null when the system raised it automatically (Phase 9 velocity
 * checks — {@link com.scansettle.api.fraud.PaymentVelocityGuard}), not an ops user.
 */
@Entity
@Table(name = "fraud_flag")
public class FraudFlag {

    public enum Status {
        ACTIVE, CLEARED
    }

    @Id
    private UUID id;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "raised_by")
    private UUID raisedBy;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "cleared_by")
    private UUID clearedBy;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    protected FraudFlag() {
        // JPA
    }

    public FraudFlag(UUID id, UUID merchantId, UUID paymentId, String reason, UUID raisedBy) {
        this.id = id;
        this.merchantId = merchantId;
        this.paymentId = paymentId;
        this.reason = reason;
        this.status = Status.ACTIVE;
        this.raisedBy = raisedBy;
        this.raisedAt = Instant.now();
    }

    public void clear(UUID clearedBy) {
        this.status = Status.CLEARED;
        this.clearedBy = clearedBy;
        this.clearedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getRaisedBy() {
        return raisedBy;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public UUID getClearedBy() {
        return clearedBy;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }
}
