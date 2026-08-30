package com.scansettle.api.openbanking.model;

/**
 * The provider's raw view of a payment's status. This is deliberately a smaller,
 * provider-shaped vocabulary — mapping it onto ScanSettle's own Payment state
 * machine (docs/payment-states.md) is the payments module's job (Phase 4), not this
 * module's. Keeping the mapping at the boundary is what stops a provider's status
 * model leaking into the domain.
 */
public enum ProviderPaymentStatus {
    PENDING,
    SUBMITTED,
    CONFIRMED,
    REJECTED,
    FAILED,
    CANCELLED,
    EXPIRED
}
