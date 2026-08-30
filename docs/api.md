# ScanSettle — API Catalogue

Status: Phase 0 catalogue below is preliminary design; **"Built"** rows reflect what
actually shipped through Phase 9 (merchant auth/MFA is self-issued JWT, not OIDC yet —
see docs/architecture.md). Tables (Phase 6), Admin/ops (Phase 8) and hardening
(Phase 9, ADR-0011) are all fully built; refund is still design-only (ADR-0004), and
POS/real-Open-Banking-provider endpoints remain design-only pending Phase 7/5 (both
explicitly parked). Every public/customer-facing endpoint below is also rate-limited
per-IP (docs/security-audit-phase9.md).

## Conventions

- REST/JSON, versioned under `/api/v1`.
- Errors use RFC 7807 Problem Details (`application/problem+json`): `type`, `title`,
  `status`, `detail`, `instance`, plus a `correlationId` extension field.
- Every response includes `X-Correlation-Id` (echoing the request's, or generated).
- Mutating POSTs that create a financial record require an `Idempotency-Key` header.
- List endpoints are cursor-paginated (`?cursor=&limit=`), returning `nextCursor`.
- Merchant-authenticated endpoints require a bearer token (OIDC) and are scoped to the
  caller's merchant + role automatically — the merchant ID is never taken from the
  request body/path for authenticated endpoints.

## Merchant-authenticated APIs

| Method & Path | Purpose | Notes |
|---|---|---|
| `POST /merchants` | Merchant self-registration | Public. **Built** |
| `POST /auth/login` | Merchant login | Public. Returns `accessToken` or `mfaChallengeToken` if MFA is enabled. **Built** (self-issued JWT, not OIDC yet) |
| `POST /auth/mfa/enroll` / `POST /auth/mfa/verify` | MFA (TOTP) self-enrollment | Authenticated self-service. **Built** |
| `POST /auth/mfa/verify-login` | Complete login after MFA challenge | Public (the challenge token itself is the credential). **Built** |
| `GET /merchant-users` / `POST /merchant-users` / `PATCH /merchant-users/{id}` | Manage staff & roles | RBAC: ADMIN+ for write. **Built** (no invite email yet — temporary password set directly) |
| `GET /merchant/profile` / `PATCH /merchant/profile` | Business profile | **Built** |
| `GET /merchant/bank-account` / `PUT /merchant/bank-account` | Bank account | ADMIN+, audited, AES-256-GCM at rest. `PUT` requires `currentPassword` (+ `mfaCode` if MFA enabled) — step-up re-auth, ADR-0011. **Built** |
| `GET /payments` | List/search payments | Filters: `state`, `from`, `to`; paginated. **Built** |
| `GET /payments/{paymentId}` | Payment detail | **Built** |
| `POST /payments/{paymentId}/refund` | Request a refund | Design-only — Phase 4, scoped per ADR-0004 |
| `POST /payment-links` | Create a ScanSettle Link | STAFF+. Returns link URL; QR is a separate endpoint. **Built** |
| `GET /payment-links` | List payment links | **Built** |
| `GET /payment-links/{linkId}` | Link detail/status | **Built** |
| `GET /payment-links/{linkId}/qr` | QR code (PNG) for a link | **Built** — generated on demand, never persisted |
| `GET /dashboard/summary` | Dashboard stat row (today/month/fees/pending) | **Built** |
| `GET /venues` / `POST /venues` | Manage venues | ADMIN+ to create, READ_ONLY+ to list. **Built** |
| `GET /venues/{venueId}/tables` / `POST /venues/{venueId}/tables` | Manage tables | Returns `qrToken` and `occupancyStatus` (`FREE`/`OCCUPIED`, ADR-0009). **Built** |
| `POST /tables/{tableId}/bill` | Open a bill on a table | STAFF+. Manual line-item entry — no POS integration until Phase 7 (docs/pos-integration.md). **Built** |
| `POST /tables/{tableId}/bill/items` | Add more line items to the table's currently-open bill | STAFF+. For an occupied table running a tab (ADR-0009) — 409 if the table has no open bill. **Built** |
| `PATCH /bills/{billId}/items/{itemId}` | Amend an existing line item's description/amount | STAFF+. Recalculates the bill total. 409 if the bill isn't open (ADR-0009 addendum). **Built** |
| `DELETE /bills/{billId}/items/{itemId}` | Remove a line item | STAFF+. Recalculates the bill total. 409 if the bill isn't open, or if it's the last remaining item (void the bill instead). **Built** |
| `POST /bills/{billId}/void` | Void a bill | STAFF+. **Built** |
| `GET /audit-events` | Audit trail | ADMIN+. **Built** |

## Public / customer-facing APIs (no auth)

| Method & Path | Purpose | Notes |
|---|---|---|
| `GET /payment-links/{linkId}/public` | Public view of a link for the pay page | Minimal data: amount, description, reference, payable. **Built** |
| `POST /payment-links/{linkId}/payments` | Start a payment attempt against a link | Idempotency-Key supported. Calls `OpenBankingProvider.createAuthorisation()`. **Built** |
| `GET /payments/{paymentId}/status` | Poll status (source of truth, not the browser redirect) | Syncs from the provider on each call. **Built** |
| `GET /open-banking/banks` | Supported bank list for bank-selection screen | Proxies `OpenBankingProvider.getSupportedBanks()`. **Built** (Phase 2) |
| `GET /tables/scan/{qrToken}` | Venue + table + current bill in one call — what a QR scan hits | Returns `occupancyStatus`; `bill` is `null` when `FREE` (ADR-0009 — a settled table stops serving its old bill's numbers). **Built** |
| `GET /bills/{billId}` | Public bill view (refresh remaining balance mid-flow) | **Built** |
| `POST /bills/{billId}/payments` | Pay full/partial/split/custom amount (+ tip) | Reservation flow (scansettle-tables.md, ADR-0003). **Built** |

## Provider webhook ingress

| Method & Path | Purpose | Notes |
|---|---|---|
| `POST /webhooks/open-banking` | Inbound Open Banking provider events | Public — authenticated by HMAC signature (`X-Webhook-Signature`) inside the handler, never a merchant JWT. Idempotent on `(provider, providerEventId)`; rejects on bad signature or a stale (>5 min) timestamp. **Built** (against the mock provider; Phase 5 wires a real provider's signature scheme the same way) |

## Merchant-authenticated reconciliation

| Method & Path | Purpose | Notes |
|---|---|---|
| `GET /reconciliation` | Reconciliation records (expected vs. confirmed amount per payment) | ADMIN+. **Built** — populated automatically whenever a payment reaches a terminal outcome |

## Dev/test-only APIs (never available outside `dev`/`test` profiles)

| Method & Path | Purpose |
|---|---|
| `POST /dev/token` | Issue an access token for a chosen role — pre-registration RBAC scaffolding from Phase 2, superseded by real login for merchant-scoped work |
| `GET /mock-bank/{providerReference}` / `POST /mock-bank/{providerReference}/decision` | ScanSettle's own stand-in for a real bank's login/consent screen — where the mock provider's redirect sends the customer. The decision endpoint fires a genuinely signed webhook through the same `WebhookIngestionService` the real endpoint uses (ADR-0007), not a shortcut |
| `POST /dev/payments/{paymentId}/simulate-provider-status` | Direct-mutation shortcut from Phase 3, kept for fast test fixtures — superseded for real flows by the mock-bank + webhook path above |
| `POST /webhooks/pos` | Inbound POS provider events — design-only, Phase 7 |

## Admin/ops APIs (Phase 8)

ScanSettle's own internal ops/support staff — a genuinely separate persona from
merchant users, authenticated with its own login and never scoped to a merchant
(ADR-0010, `Role.PLATFORM_ADMIN`). None of these accept a merchant JWT, and no
merchant endpoint accepts an ops token.

| Method & Path | Purpose | Notes |
|---|---|---|
| `POST /admin/auth/login` | Ops login | Public. Returns `accessToken` or `mfaChallengeToken` if MFA is enabled. **Built** |
| `POST /admin/auth/mfa/enroll` / `POST /admin/auth/mfa/verify` | MFA (TOTP) self-enrollment for ops logins | Authenticated self-service, off by default (ADR-0011). **Built** |
| `POST /admin/auth/mfa/verify-login` | Complete ops login after MFA challenge | Public (the challenge token itself is the credential). **Built** |
| `GET /admin/merchants` | Merchant list/search (`?q=tradingName`), verification status | **Built** |
| `POST /admin/merchants/{id}/suspend` | Suspend a merchant | Blocks that merchant's users from logging in. **Built** |
| `POST /admin/merchants/{id}/reactivate` | Reactivate a suspended merchant | Not in the original sketch but the obvious, necessary counterpart (ADR-0010). **Built** |
| `GET /admin/payments/{id}/investigate` | Full provider-transaction + webhook history + reconciliation for a payment | **Built** |
| `GET /admin/webhooks` | Webhook inspection (received, signature status, processing result) | Paginated, newest first. **Built** |
| `GET /admin/fraud-flags` | List fraud flags (optional `?merchantId=`) | Not in the original sketch but required to see/manage what's raised (ADR-0010). **Built** |
| `POST /admin/fraud-flags` | Raise a fraud flag on a merchant or payment | Exactly one of `merchantId`/`paymentId`. **Built** |
| `POST /admin/fraud-flags/{id}/clear` | Clear a fraud flag | **Built** |

## Error model example

```json
{
  "type": "https://scansettle.com/problems/insufficient-remaining-balance",
  "title": "Amount exceeds remaining balance",
  "status": 409,
  "detail": "Requested £30.00 but only £10.00 remains unreserved on this bill.",
  "instance": "/api/v1/bills/b_123/payments",
  "correlationId": "c_9f1a..."
}
```
