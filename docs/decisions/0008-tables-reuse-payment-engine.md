# ADR-0008: ScanSettle Tables reuses the Payment/webhook engine via a domain-event seam

Status: Accepted
Date: 2026-08-30

## Context

Phase 6 needed a Tables payment attempt to go through the exact same state machine,
webhook verification, and fee calculation that Phase 3/4 already built for
ScanSettle Links — the schema was designed for this from Phase 2
(`payment.bill_payment_id`, `provider_transaction.bill_payment_id`). The question was
how `tables` and `payments` should talk to each other without creating a circular
package dependency (`payments` would need to update `Bill`/`BillPayment`/
`BillPaymentReservation` state on confirmation, which lives in `tables`).

## Decision

`Payment.forBillPayment(...)` is a second factory alongside the existing
constructor, so a bill-payment attempt is a first-class `Payment` — same state
machine, same `PaymentService.startPayment`-equivalent flow, same
`WebhookIngestionService` path. A new `PaymentOutcomeListener` interface lives in
`payments`; `PaymentService` calls every registered listener when a payment reaches
a terminal state. `tables` registers `BillPaymentOutcomeListener`, which commits or
releases the originating `BillPaymentReservation` and recomputes `Bill` state. The
dependency points one way only: `tables` → `payments`, never the reverse.

## Consequences

- The reservation-based concurrency guarantee (ADR-0003) and the real webhook path
  (ADR-0007) both apply to Tables payments with zero new state-machine or
  signature-verification code — only the domain-specific bit (reservations, split
  bills) is new.
- `BillPaymentConcurrencyIT`'s true multi-threaded tests exercise the same
  `PaymentState` transition-walking logic proven in Phase 4, giving high confidence
  the reuse didn't quietly break anything.
- Adding a future third payment "flavour" (e.g. a subscription or recurring charge,
  well beyond MVP) would follow the same pattern — a `Payment.forX(...)` factory and
  an `X`-specific `PaymentOutcomeListener` — without touching `PaymentService` or
  `WebhookIngestionService` again.
