# ADR-0004: Refund scoped to request/record, not automatic reversal

Status: Proposed — awaiting Product Owner approval
Date: 2026-08-29

## Context

The brief asks for "refund request where supported" (Section 8) and a `Refund`
entity. Open Banking payment initiation is a *push* payment — there is no
scheme-level mechanism (unlike card chargebacks) for ScanSettle or the Open Banking
provider to pull money back from the merchant's account automatically. A genuine
refund requires the merchant's bank to push a new payment back to the customer.

## Decision

For MVP, `Refund` is a **trackable request/record**, not an automated reversal:

- Merchant (or ops) creates a `Refund` request against a `Payment`, with status
  `REQUESTED`.
- Fulfilment happens outside ScanSettle's payment rails for MVP — the merchant makes
  the return payment via their own online banking, then marks the refund
  `MANUALLY_SETTLED` in ScanSettle (with an optional reference), which is audited.
- `OpenBankingProvider.refundPayment()` remains on the interface for future providers
  that expose a genuine payout capability, but the domain layer never assumes it is
  available — it's a capability flag per adapter, checked before offering an
  "automatic" refund option in the UI.

## Consequences

- Customer/merchant-facing language must say "refund request", not "refund" or
  "automatic refund" — avoids a trust/regulatory-complaint risk (architecture.md,
  Regulatory Assumptions).
- No dependency on a merchant having outbound payment-initiation capability for MVP.
- If a chosen `{{OPEN_BANKING_PROVIDER}}` later supports payouts, the automatic path
  can be enabled per-merchant without a domain-model change — only the capability
  flag and UI need to change.

## Approval needed

This narrows what "refund" delivers versus a naive reading of the brief's entity
list — flagged for explicit Product Owner sign-off, since it affects merchant-facing
promises.
