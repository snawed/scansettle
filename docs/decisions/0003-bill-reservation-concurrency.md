# ADR-0003: Reservation-based concurrency control for ScanSettle Tables

Status: Proposed — awaiting Product Owner approval
Date: 2026-08-29

## Context

Multiple customers can pay against the same bill concurrently. The brief requires
that concurrent attempts never allow committed payments to exceed the bill total
(Section 23).

## Decision

Introduce `BillPaymentReservation`, created inside a single short database
transaction (row lock on `Bill`) at the moment a customer confirms an amount and
*before* any Open Banking redirect is initiated. Reservations expire automatically if
not committed within a TTL. See scansettle-tables.md for full mechanics, worked
example, and sequence diagram.

## Consequences

- Overpayment is structurally prevented, not just discouraged by UI hints.
- A customer whose requested amount no longer fits is told immediately, before a
  wasted trip to their bank.
- Requires a scheduled sweep job to expire abandoned reservations — a small new
  operational component (in-process Spring scheduled task, no new infra).
- Adds one new entity not in the brief's starter list — flagged per Working Rule 27.

## Alternatives considered

- Optimistic concurrency (retry on conflicting `Bill.version` update at commit time)
  — rejected as the primary mechanism: it would let a payment travel all the way
  through bank authentication before discovering it can't be honoured, which is a
  worse customer experience and creates exactly the "money authorised but nowhere to
  put it" problem reservations are meant to avoid.
