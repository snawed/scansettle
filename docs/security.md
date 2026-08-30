# ScanSettle — Security Model (Phase 0)

Status: DRAFT — awaiting Product Owner approval

## 1. Principles

- No customer bank credentials ever reach ScanSettle (Open Banking redirect model
  guarantees this structurally, not just by policy).
- No Open Banking/POS credentials in source code — placeholders only
  (`{{OPEN_BANKING_CLIENT_SECRET}}` etc.), resolved via environment/secrets manager.
- Money amounts are never floating point; `Money` value object = integer minor units
  (pence) + currency code.
- Every payment-affecting mutation produces an `AuditEvent`.
- Every query is merchant-scoped; tenant isolation is enforced in the service layer
  (not left to callers to remember a `WHERE merchant_id = ?`) — see Section 21 of the
  brief, and Multi-Tenancy below.

## 2. Controls by area

| Area | Control |
|---|---|
| Transport | TLS everywhere; HSTS; secure cookies |
| Data at rest | Postgres encryption at rest; bank account numbers/sort codes encrypted at the application layer (not just disk-level) so a DB dump alone doesn't expose them |
| Merchant auth | OIDC/OAuth2-compatible IdP; MFA (TOTP minimum) required for OWNER/ADMIN roles at minimum, configurable to require for all roles |
| Authorisation | RBAC (OWNER/ADMIN/STAFF/READ_ONLY) enforced server-side on every endpoint, not just hidden in the UI; permission matrix defined and tested per role before Phase 3 |
| Ops/admin auth | `PLATFORM_ADMIN` is a standalone role, deliberately excluded from the merchant OWNER>ADMIN>STAFF>READ_ONLY hierarchy — a platform admin's token can never satisfy a merchant `hasRole('ADMIN')` check, and vice versa (ADR-0010, Phase 8). Separate login (`POST /admin/auth/login`), separate `platform_admin_user` table, no self-registration |
| Secrets | Never in source/version control; environment/secrets-manager only; rotated credentials supported by config, not code change |
| Webhook signature validation | Every inbound webhook (Open Banking, POS) verified against `{{OPEN_BANKING_WEBHOOK_SECRET}}` / provider-equivalent before any processing; invalid signature → rejected + audited, never silently dropped |
| Webhook replay protection | `WebhookEvent` unique constraint on `(provider, providerEventId)`; duplicate deliveries are acknowledged (200) but not reprocessed; timestamp-window check to reject stale/replayed signed payloads |
| API rate limiting | Applied per-IP and per-merchant on public/customer-facing endpoints (pay pages, bill views) to blunt scraping/abuse; MVP implementation via a Postgres-backed or in-process token bucket (no new infra — see architecture.md Section 12 decision) |
| CORS | Explicit allow-list of frontend origins (`app.cors.allowed-origins` / `APP_CORS_ALLOWED_ORIGINS`), never a wildcard, since the bearer token travels as a credential; no origin is trusted by default |
| Idempotency | `Idempotency-Key` header required on `POST /payments`, `POST /bills/{id}/payments`, `POST /payment-links`; duplicate key with same payload returns the original result, not a second payment |
| Input validation | All request DTOs validated (Bean Validation); amounts checked against currency-appropriate ranges/precision before any provider call |
| Secure headers | CSP, X-Content-Type-Options, X-Frame-Options/frame-ancestors, Referrer-Policy on all responses |
| Audit logging | Append-only `AuditEvent`; includes actor, action, entity, before/after, correlation ID; no audit record is ever updated or deleted by application code |
| Fraud/velocity controls | Per-merchant and per-payer(IP/device) velocity checks on payment creation (MVP: basic thresholds + alerting; full fraud engine out of scope) |
| Bank account changes | `MerchantBankAccount` changes require re-authentication (current password, plus a fresh TOTP code if MFA is enabled) and produce a dedicated high-visibility `AuditEvent` — built in Phase 9 (ADR-0011). A "recently changed" cool-off flag in the dashboard UI is not built (no bank-account UI exists yet at all) |
| PII minimisation | No `Customer` entity; only optional, non-authenticated contact fields on `Payment`/`BillPayment` for receipts |
| Correlation IDs | Every request gets/propagates an `X-Correlation-Id`, threaded through logs, traces, and audit events, from customer browser action through webhook processing |

## 3. Multi-tenancy isolation (Section 21 of the brief)

- Every merchant-scoped table carries `merchant_id` (directly, or transitively via
  `venue_id`/`bill_id` for Tables entities).
- Application-layer enforcement: repository/service methods that return
  merchant-scoped data always take the authenticated merchant context as a mandatory
  parameter — there is no "get payment by ID" method that skips the merchant check.
- Customer-facing endpoints (pay pages, table bills) are scoped by opaque,
  unguessable tokens (`PaymentLink` ID, `Table.qrToken`), not sequential IDs, and
  return only the minimum data needed to pay.
- Tenant isolation is explicitly covered by an automated test suite (Phase 3
  onward): every merchant-scoped endpoint tested with a second merchant's
  authenticated user attempting access, asserting a 404/403, not merely a filtered
  empty result (which can leak existence).

## 4. Phase 9 (Security & Hardening) — complete for MVP scope

Full findings in docs/security-audit-phase9.md and ADR-0011. Summary: SQLi, XSS,
CSRF, SSRF, and container security were all reviewed with no active
vulnerabilities found (each with a concrete, code-level reason why, not a
checklist tick); dependency scanning couldn't run live in this offline sandbox
for the backend, so real, continuous scanning was wired into CI instead
(Dependabot + `npm audit`) rather than faked. Concrete gaps closed: secure
response headers (CSP/Referrer-Policy/Permissions-Policy), per-IP rate limiting
on every public endpoint, bank-account step-up re-authentication, MFA for ops
logins, and automated per-IP/per-merchant payment-velocity flagging.
