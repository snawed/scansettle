package com.scansettle.api.payments;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** docs/payment-states.md — the refined 10-state machine from Phase 0. */
public enum PaymentState {
    CREATED,
    AWAITING_PAYMENT,
    REDIRECTED_TO_BANK,
    PAYMENT_SUBMITTED,
    PAYMENT_PENDING,
    PAYMENT_CONFIRMED(true),
    FAILED(true),
    REJECTED(true),
    CANCELLED(true),
    EXPIRED(true);

    private static final Map<PaymentState, Set<PaymentState>> ALLOWED_TRANSITIONS = Map.of(
            CREATED, Set.of(AWAITING_PAYMENT),
            AWAITING_PAYMENT, Set.of(REDIRECTED_TO_BANK, EXPIRED, CANCELLED),
            REDIRECTED_TO_BANK, Set.of(PAYMENT_SUBMITTED, REJECTED, FAILED, EXPIRED),
            PAYMENT_SUBMITTED, Set.of(PAYMENT_PENDING, PAYMENT_CONFIRMED, REJECTED, FAILED),
            PAYMENT_PENDING, Set.of(PAYMENT_CONFIRMED, FAILED, REJECTED),
            PAYMENT_CONFIRMED, Set.of(),
            FAILED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of(),
            EXPIRED, Set.of());

    private final boolean terminal;

    PaymentState() {
        this(false);
    }

    PaymentState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(PaymentState target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * A provider status update reports where a payment ended up, not every
     * intermediate hop it passed through (e.g. it may confirm without a separately
     * observed "submitted" event). This returns the shortest legal chain of states
     * from here to {@code target} (excluding {@code this}, including {@code target}),
     * so the caller can walk each hop through {@link Payment#transitionTo} rather
     * than attempting an illegal direct jump. Empty if {@code target} is unreachable.
     */
    public List<PaymentState> pathTo(PaymentState target) {
        if (this == target) {
            return List.of();
        }
        Map<PaymentState, PaymentState> cameFrom = new LinkedHashMap<>();
        ArrayDeque<PaymentState> queue = new ArrayDeque<>();
        queue.add(this);
        cameFrom.put(this, null);

        while (!queue.isEmpty()) {
            PaymentState current = queue.poll();
            if (current == target) {
                break;
            }
            for (PaymentState next : ALLOWED_TRANSITIONS.getOrDefault(current, Set.of())) {
                if (!cameFrom.containsKey(next)) {
                    cameFrom.put(next, current);
                    queue.add(next);
                }
            }
        }

        if (!cameFrom.containsKey(target)) {
            return List.of();
        }
        java.util.LinkedList<PaymentState> path = new java.util.LinkedList<>();
        for (PaymentState at = target; at != null; at = cameFrom.get(at)) {
            path.addFirst(at);
        }
        path.removeFirst(); // drops `this` — the loop above includes it as the walk's origin
        return path; // starts with the first hop after `this`, ends with `target`
    }
}
