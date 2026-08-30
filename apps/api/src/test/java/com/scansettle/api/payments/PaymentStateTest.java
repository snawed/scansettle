package com.scansettle.api.payments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStateTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(PaymentState.CREATED.canTransitionTo(PaymentState.AWAITING_PAYMENT)).isTrue();
        assertThat(PaymentState.AWAITING_PAYMENT.canTransitionTo(PaymentState.REDIRECTED_TO_BANK)).isTrue();
        assertThat(PaymentState.REDIRECTED_TO_BANK.canTransitionTo(PaymentState.PAYMENT_SUBMITTED)).isTrue();
        assertThat(PaymentState.PAYMENT_SUBMITTED.canTransitionTo(PaymentState.PAYMENT_CONFIRMED)).isTrue();
    }

    @Test
    void cannotSkipStraightFromCreatedToConfirmed() {
        assertThat(PaymentState.CREATED.canTransitionTo(PaymentState.PAYMENT_CONFIRMED)).isFalse();
    }

    @Test
    void cannotMoveBackwardsFromSubmittedToAwaitingPayment() {
        assertThat(PaymentState.PAYMENT_SUBMITTED.canTransitionTo(PaymentState.AWAITING_PAYMENT)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentState.class, names = {"PAYMENT_CONFIRMED", "FAILED", "REJECTED", "CANCELLED", "EXPIRED"})
    void terminalStatesHaveNoOutgoingTransitions(PaymentState terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (PaymentState candidate : PaymentState.values()) {
            assertThat(terminal.canTransitionTo(candidate)).isFalse();
        }
    }

    @Test
    void nonTerminalStatesAreNotMarkedTerminal() {
        assertThat(PaymentState.CREATED.isTerminal()).isFalse();
        assertThat(PaymentState.PAYMENT_PENDING.isTerminal()).isFalse();
    }

    @Test
    void pathToWalksThroughTheRequiredIntermediateHop() {
        // A provider confirming directly from REDIRECTED_TO_BANK must walk through
        // PAYMENT_SUBMITTED — there is no direct edge (docs/payment-states.md).
        assertThat(PaymentState.REDIRECTED_TO_BANK.pathTo(PaymentState.PAYMENT_CONFIRMED))
                .containsExactly(PaymentState.PAYMENT_SUBMITTED, PaymentState.PAYMENT_CONFIRMED);
    }

    @Test
    void pathToASingleLegalHopIsJustThatHop() {
        assertThat(PaymentState.PAYMENT_SUBMITTED.pathTo(PaymentState.PAYMENT_CONFIRMED))
                .containsExactly(PaymentState.PAYMENT_CONFIRMED);
    }

    @Test
    void pathToAnUnreachableStateIsEmpty() {
        // PAYMENT_CONFIRMED is terminal — nothing is reachable from it.
        assertThat(PaymentState.PAYMENT_CONFIRMED.pathTo(PaymentState.FAILED)).isEmpty();
    }

    @Test
    void everyHopOnAWalkedPathIsIndividuallyLegal() {
        for (PaymentState from : PaymentState.values()) {
            for (PaymentState to : PaymentState.values()) {
                var path = from.pathTo(to);
                PaymentState cursor = from;
                for (PaymentState hop : path) {
                    assertThat(cursor.canTransitionTo(hop))
                            .as("%s -> %s must be a legal single hop within path %s -> %s", cursor, hop, from, to)
                            .isTrue();
                    cursor = hop;
                }
            }
        }
    }
}
