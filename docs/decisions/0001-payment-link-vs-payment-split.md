# ADR-0001: Split `PaymentLink` (reusable request) from `Payment` (single attempt)

Status: Proposed — awaiting Product Owner approval
Date: 2026-08-29

## Context

The brief's starter domain list has a single `Payment` entity playing double duty as
both "the thing the merchant sent the customer" and "one attempt to pay it". A
customer can open a link, pick a bank, fail SCA, and retry — potentially more than
once — without the merchant creating a new link each time.

## Decision

Introduce `PaymentLink` as the durable, shareable object (amount, description,
reference, expiry) and `Payment` as a single attempt against it (or against a `Bill`,
for Tables). `PaymentLink` has zero-to-many `Payment` attempts.

## Consequences

- Retry UX is clean: customer failing a bank auth can try again on the same link
  without the merchant re-issuing anything.
- QR codes stay stable across retries (they encode the link, not a specific attempt).
- Slightly more schema than a single-table model, but avoids overloading one entity
  with two different lifecycles (a link can be "active" for days; a payment attempt
  is a short-lived flow).

## Alternatives considered

- Single `Payment` entity with a "retry" self-reference — rejected as more awkward to
  query and reason about than a clean parent/child split.
