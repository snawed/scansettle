package com.scansettle.api.openbanking.model;

/**
 * {@code signatureValid=false} must always short-circuit before any state change —
 * see docs/security.md (webhook signature validation, replay protection).
 */
public record WebhookProcessingResult(
        boolean signatureValid,
        String providerReference,
        ProviderPaymentStatus status,
        String providerEventId
) {
}
