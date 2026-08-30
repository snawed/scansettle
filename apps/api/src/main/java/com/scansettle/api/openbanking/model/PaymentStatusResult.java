package com.scansettle.api.openbanking.model;

import java.time.Instant;

public record PaymentStatusResult(
        String providerReference,
        ProviderPaymentStatus status,
        Instant lastUpdated,
        String reasonCode
) {
}
