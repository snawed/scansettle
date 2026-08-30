# ADR-0002: Add `Venue` between `Merchant` and `Table`

Status: Proposed — awaiting Product Owner approval
Date: 2026-08-29

## Context

The brief's entity list has `Table` implicitly belonging to `Merchant`. Hospitality
merchants commonly operate more than one site (a pub group, a small chain), each with
its own tables and potentially its own POS connection.

## Decision

Add a `Venue` entity between `Merchant` and `Table`/`POSConnection`. A single implicit
`Venue` is auto-created for trade/professional merchants so this is invisible to that
persona.

## Consequences

- Multi-site hospitality merchants are supported from day one without a breaking
  schema migration.
- One extra join for every table/bill query — negligible cost.
- Slightly more setup complexity in the merchant onboarding flow for hospitality
  merchants (must create/select a venue) — mitigated by auto-creating a default venue
  named after the business on signup.

## Alternatives considered

- `Table.merchantId` directly, add `Venue` later if/when a multi-site merchant signs
  up — rejected: retrofitting a new required parent onto `Table`/`Bill` after real
  data exists is a materially more disruptive migration than including it now.

## Approval needed

This changes the schema shape proposed in the brief's entity list — flagged per
Working Rule 27 for explicit Product Owner approval before Phase 3 build.
