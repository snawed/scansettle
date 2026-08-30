# ADR-0010: ScanSettle ops/support get a genuinely separate login, not a merchant role

Status: Accepted
Date: 2026-08-30

## Context

Phase 8 needs cross-merchant admin/ops tooling: merchant list/suspend, payment
investigation, webhook inspection, fraud flags. docs/architecture.md's persona
table already names "ScanSettle Ops/Support" as a distinct internal persona from
any merchant user, and `AuthenticatedPrincipal`'s own doc comment — written back
in Phase 2 — already anticipated this: *"merchantId is null for ScanSettle
ops/internal principals."* The question was how to actually build that principal.

The tempting shortcut would be adding an `ADMIN`-and-above check that also passes
for some special merchant, or bolting a "platform" flag onto `MerchantUser`. Both
would let a merchant-side credential reach cross-merchant data — a real tenant-
isolation break, not just an ugly abstraction, given docs/security.md treats
tenant isolation as a first-class, tested invariant from Phase 3 onward.

## Decision

Ops gets its own table (`platform_admin_user`), its own login endpoint
(`POST /admin/auth/login`), and its own role (`Role.PLATFORM_ADMIN`) — deliberately
**not** added to the `RoleHierarchyConfig` chain (OWNER>ADMIN>STAFF>READ_ONLY), so
a platform admin's token can never satisfy a merchant-scoped `hasRole('ADMIN')`
check, and a merchant token can never satisfy `hasRole('PLATFORM_ADMIN')`. Every
existing piece of auth infrastructure is reused as-is: `JwtService.issue()` already
accepts a null `merchantId`, `AuthenticatedPrincipal` already models it, and
`AuditEvent.ActorType.OPS` already existed unused since Phase 2 — this is
new admin surface built on old, load-bearing plumbing, not a parallel auth system.

Two additions beyond the docs/api.md sketch, both judged necessary rather than
scope creep: `POST /admin/merchants/{id}/reactivate` (a suspend button with no way
back is a real operational gap, not a nice-to-have), and `GET /admin/fraud-flags`
(raising/clearing a flag you can never list back is not a usable feature).

There is no self-registration for ops accounts — unlike merchants, anyone being
able to mint themselves a platform-admin login would defeat the whole point. For
MVP, exactly one ops account is seeded via Flyway (`V6__seed_platform_admin.sql`,
a placeholder dev credential, documented as such). Real ops-account provisioning
(inviting additional ops staff, credential rotation, MFA for ops logins) is
explicitly deferred — proper ops-account lifecycle is hardening work, matching
docs/security.md's existing Phase 9 deferral of the broader security/hardening
pass, not a Phase 8 concern.

Suspending a merchant needed to actually do something to be worth building:
`AuthService.login()` now checks the owning `Merchant.status` after password
verification and before issuing a token, rejecting with a distinct
`merchant-suspended` error. This was a genuine gap closed as part of this phase,
not scope creep — an admin "suspend" button that suspended nothing would be a
half-finished feature.

`WebhookEvent` gained a `providerReference` column (populated from the same
`WebhookProcessingResult.providerReference()` the ingestion service already parses
per event, signature-valid or not) so `GET /admin/payments/{id}/investigate` can
query webhook history directly instead of scanning the jsonb payload column.

## Consequences

- Tenant isolation holds structurally for the new surface the same way it already
  does for merchant endpoints — by construction (a disjoint role), not convention.
- The frontend gets its own isolated ops session: `/ops/*` routes, a separate
  `opsApiClient.js` with its own `localStorage` key, so a merchant session and an
  ops session can coexist in the same browser without either clobbering the other.
- A future "invite another ops user" flow (Phase 9 territory) just needs a
  `POST /admin/users`-style endpoint on the same `PlatformAdminUser` table — no
  schema or auth-model rework.
