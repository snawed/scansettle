package com.scansettle.api.openbanking.model;

/**
 * {@code supported=false} is the expected outcome for most providers/rails for MVP
 * (see ADR-0004) — callers must not assume a refund attempt moves money.
 */
public record RefundResult(boolean supported, String providerReference, String message) {

    public static RefundResult unsupported() {
        return new RefundResult(false, null,
                "This provider does not support automated refunds for push payments. "
                        + "Refund must be fulfilled by the merchant's own bank transfer.");
    }
}
