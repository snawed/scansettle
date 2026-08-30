package com.scansettle.api.payments;

/**
 * A lightweight internal domain-event seam: {@link PaymentService} calls every
 * registered listener whenever a payment reaches a terminal state, regardless of
 * what kind of payment it is. This is what lets the {@code tables} module react to
 * bill-payment outcomes (committing/releasing a reservation) without {@code payments}
 * having to depend on {@code tables} — the dependency points one way only.
 */
public interface PaymentOutcomeListener {

    void onPaymentReachedTerminalState(Payment payment);
}
