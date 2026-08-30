# ADR-0006: Phase 3 built the full payment domain, not a lightweight placeholder

Status: Accepted
Date: 2026-08-29

## Context

Phase 0's brief listed "Create payment" under Phase 3 (Merchant Core) but also listed
"Payment domain, Payment state machine, ScanSettle Links, QR generation,
OpenBankingProvider abstraction, Mock provider" under Phase 4 (Payments Engine) —
a genuine overlap. Building the lightweight version first risked redoing the same
work in Phase 4; building the full version now risked scope creep without
Product Owner sign-off.

## Decision

Asked the Product Owner directly. They chose the full build: Phase 3 delivers
`PaymentLink`, `Payment` with the complete 10-state machine, QR generation, and real
`MockOpenBankingProvider` calls (`createAuthorisation`, `getPaymentStatus`). Phase 4
still owns exactly what was agreed: the customer-facing pay page UI, provider
webhooks (push-based ingestion with signature verification/idempotency), and
reconciliation. The dev-only `simulate-provider-status` endpoint stands in for real
webhooks until Phase 4 builds them.

## Consequences

- Phase 3's "Create Payment" screen is real, not a stub — it generates a working
  link, QR, and the payment can be driven through its full lifecycle via the API
  today (verified end-to-end, both by automated tests and a live browser run).
- Phase 4's scope is now narrower and clearer: customer pay page, real webhook
  ingestion (replacing the dev simulate endpoint), idempotent webhook processing,
  reconciliation foundation. No payment-domain rework needed.
- `docs/api.md` was updated to mark each endpoint **Built** vs. design-only, since
  the Phase 0 catalogue no longer matches phase boundaries exactly.
