package com.scansettle.api.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The durable, shareable artefact a merchant creates (docs/decisions/0001) — a
 * customer may attempt to pay it more than once (retry after a failed bank auth),
 * each attempt being a separate {@link Payment}.
 */
@Entity
@Table(name = "payment_link")
public class PaymentLink {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentLink() {
        // JPA
    }

    public PaymentLink(UUID id, UUID merchantId, long amountMinorUnits, String currencyCode, String description,
                        String reference, Instant expiresAt, UUID createdBy) {
        this.id = id;
        this.merchantId = merchantId;
        this.amountMinorUnits = amountMinorUnits;
        this.currencyCode = currencyCode;
        this.description = description;
        this.reference = reference;
        this.status = Status.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public boolean isPayable() {
        if (status != Status.ACTIVE) {
            return false;
        }
        return expiresAt == null || Instant.now().isBefore(expiresAt);
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Status {
        ACTIVE, EXPIRED, CLOSED
    }
}
