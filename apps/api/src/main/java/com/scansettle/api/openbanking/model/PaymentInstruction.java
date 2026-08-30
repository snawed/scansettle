package com.scansettle.api.openbanking.model;

/**
 * What ScanSettle asks a provider to collect. {@code merchantReference} is
 * ScanSettle's own Payment/BillPayment id, not a provider id — the provider
 * generates and returns its own reference in {@link ProviderPayment}.
 */
public record PaymentInstruction(
        String merchantReference,
        long amountMinorUnits,
        String currencyCode,
        String description,
        String redirectUrl,
        String selectedBankId
) {
}
