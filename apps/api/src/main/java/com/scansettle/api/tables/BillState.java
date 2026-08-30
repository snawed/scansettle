package com.scansettle.api.tables;

/**
 * docs/payment-states.md — deliberately no whole-bill PAYMENT_PENDING (a concurrent
 * payer's pending attempt must not block others; see docs/scansettle-tables.md).
 * VOIDED is a staff action, distinct from any customer-driven outcome.
 */
public enum BillState {
    OPEN,
    PARTIALLY_PAID,
    PAID,
    VOIDED
}
