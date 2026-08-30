package com.scansettle.api.payments;

import com.scansettle.api.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void illegalTransitionIsRejectedNotSilentlyApplied() {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                250000, "GBP", null, null);

        assertThatThrownBy(() -> payment.transitionTo(PaymentState.PAYMENT_CONFIRMED))
                .isInstanceOf(ConflictException.class);
        // State must be unchanged after a rejected transition.
        assertThat(payment.getState()).isEqualTo(PaymentState.CREATED);
    }

    @Test
    void legalTransitionSequenceUpdatesStateAndTimestamp() {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                9000, "GBP", null, null);
        var createdUpdatedAt = payment.getUpdatedAt();

        payment.transitionTo(PaymentState.AWAITING_PAYMENT);
        payment.transitionTo(PaymentState.REDIRECTED_TO_BANK);
        payment.transitionTo(PaymentState.PAYMENT_SUBMITTED);
        payment.transitionTo(PaymentState.PAYMENT_CONFIRMED);

        assertThat(payment.getState()).isEqualTo(PaymentState.PAYMENT_CONFIRMED);
        assertThat(payment.getUpdatedAt()).isAfterOrEqualTo(createdUpdatedAt);
    }
}
