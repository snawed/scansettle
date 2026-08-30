package com.scansettle.api.openbanking.model;

public record ProviderPayment(
        String providerReference,
        String merchantReference,
        ProviderPaymentStatus status,
        String redirectUrl
) {
}
