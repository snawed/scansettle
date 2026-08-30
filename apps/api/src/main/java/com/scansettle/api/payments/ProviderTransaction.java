package com.scansettle.api.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The only place a provider-specific reference/status string lives (docs/domain-model.md). */
@Entity
@Table(name = "provider_transaction")
public class ProviderTransaction {

    @Id
    private UUID id;

    @Column(name = "payment_id", unique = true)
    private UUID paymentId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Column(name = "raw_status", nullable = false)
    private String rawStatus;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    protected ProviderTransaction() {
        // JPA
    }

    public ProviderTransaction(UUID id, UUID paymentId, String provider, String providerReference, String rawStatus) {
        this.id = id;
        this.paymentId = paymentId;
        this.provider = provider;
        this.providerReference = providerReference;
        this.rawStatus = rawStatus;
        this.lastSyncedAt = Instant.now();
    }

    public void updateRawStatus(String rawStatus) {
        this.rawStatus = rawStatus;
        this.lastSyncedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }
}
