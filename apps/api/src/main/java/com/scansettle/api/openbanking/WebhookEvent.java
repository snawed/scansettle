package com.scansettle.api.openbanking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per inbound webhook delivery — the record that makes idempotent,
 * replay-resistant webhook processing possible (docs/security.md). The unique
 * constraint on (provider, providerEventId) is what actually enforces "never
 * process the same event twice", not application-level checking alone.
 */
@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

    public enum Source {
        OPEN_BANKING, POS
    }

    public enum ProcessingResult {
        PROCESSED, REJECTED_INVALID_SIGNATURE, REJECTED_STALE, NO_MATCHING_PAYMENT, DUPLICATE
    }

    @Id
    private UUID id;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "processing_result")
    private ProcessingResult processingResult;

    protected WebhookEvent() {
        // JPA
    }

    public WebhookEvent(UUID id, Source source, String provider, String providerEventId, boolean signatureValid,
                         String payload) {
        this(id, source, provider, providerEventId, null, signatureValid, payload);
    }

    public WebhookEvent(UUID id, Source source, String provider, String providerEventId, String providerReference,
                         boolean signatureValid, String payload) {
        this.id = id;
        this.source = source;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.providerReference = providerReference;
        this.signatureValid = signatureValid;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }

    public void markProcessed(ProcessingResult result) {
        this.processingResult = result;
        this.processedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public boolean isSignatureValid() {
        return signatureValid;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public ProcessingResult getProcessingResult() {
        return processingResult;
    }
}
