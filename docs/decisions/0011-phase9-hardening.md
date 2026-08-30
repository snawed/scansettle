# ADR-0011: Phase 9 hardening — what got built, and what's a documented review instead

Status: Accepted
Date: 2026-08-30

## Context

docs/security.md Section 4 scoped Phase 9 as: a full OWASP review, dependency
vulnerability scanning, a penetration-test checklist, targeted SSRF/CSRF/XSS/SQLi
review, container security, and a formal fraud/velocity tuning pass. Some of that
is genuinely a live-scan/tooling exercise this sandboxed, offline environment
can't run for real (no NVD access, no pentest infrastructure); the rest is
concrete code that was either missing outright or flagged as a known gap in
earlier phases and never closed.

## Decision

Split Phase 9 into two kinds of work, and did both rather than picking one:

**A structured manual review** (docs/security-audit-phase9.md), covering SQLi,
XSS, CSRF, SSRF, container security, and dependency posture — each with a real
finding based on reading the actual code, not a checklist rubber-stamp. Where a
live tool would do this better in a real deployment (dependency CVE scanning
against a current database), that's said plainly and wired into CI instead
(`.github/dependabot.yml`, an `npm audit` CI step) rather than faked with a
local scan that can't see current advisories.

**Concrete hardening**, each closing a gap the review or an earlier phase's
"flagged, not built" comment surfaced:
- Secure response headers (CSP, Referrer-Policy, Permissions-Policy) — Spring
  Security's defaults cover the rest (X-Content-Type-Options, X-Frame-Options,
  Cache-Control, disabled X-XSS-Protection); CSRF stays explicitly disabled with
  its reasoning now in a code comment, not just this doc, since bearer-token auth
  genuinely doesn't need it.
- Per-IP rate limiting (`RateLimiter`, `RateLimitFilter`) on every genuinely
  public endpoint (login, registration, MFA verify, the anonymous customer
  payment journey) — an in-process token bucket, no new infra, matching
  architecture.md's Section 12 MVP scope. Disabled under the `test` profile
  (`app.rate-limit.enabled=false` in application-test.yml): integration tests
  legitimately fire many rapid requests at these exact paths from one loopback
  address in a shared Spring context, and limiting the test suite itself would
  be false-positive flakiness, not a safeguard.
- Step-up re-authentication on `PUT /merchant/bank-account` — current password
  always required, a fresh TOTP code too if MFA is enabled; a failed attempt is
  itself audited (`BANK_ACCOUNT_CHANGE_STEP_UP_FAILED`). This was explicitly
  flagged as a known gap when the endpoint was first built ("Full step-up
  re-authentication is a Phase 9 hardening item, not built here") — closed now,
  not re-deferred.
- MFA for ops/`PLATFORM_ADMIN` logins — the same TOTP mechanism merchant users
  already have (`TotpService` is generic, reused as-is), self-enrolled via a new
  `/ops/security` page, off by default so the seeded dev account isn't locked
  out. This was the open "decision needing approval" from the Phase 8 report;
  building it now that Phase 9 is explicitly about hardening resolves it rather
  than leaving it open indefinitely.
- Automated payment-velocity checks (`PaymentVelocityGuard`,
  `VelocityTracker`) — per-IP and per-merchant sliding-window thresholds on the
  two anonymous payment-creation endpoints, reusing Phase 8's `FraudFlag`
  tooling rather than a separate alerting mechanism. Deliberately **never
  blocks a payment** — docs/security.md's own framing is "basic thresholds +
  alerting", and a false positive here would be a declined legitimate customer,
  which is a worse outcome than a missed detection at MVP scale. `FraudFlag.raisedBy`
  became nullable (`V8` migration) to represent "raised by the system" alongside
  the existing ops-raised flags from Phase 8; the same cooldown-per-cooldown-key
  pattern (an in-process map, same MVP-scope tradeoff as `RateLimiter`) stops it
  spamming a flag every request once a threshold is crossed. Also disabled under
  the `test` profile for the identical reason rate limiting is.

## Consequences

- Two new small, focused packages (`com.scansettle.api.fraud`) rather than
  folding velocity logic into `admin` or `payments` — it's a genuinely distinct
  concern (detection heuristics) from ops tooling (manual investigation) or the
  payment domain itself, and the doc already treats "Fraud/velocity controls" as
  its own control-matrix line.
- Both new in-process trackers (`RateLimiter`, `VelocityTracker`) share the same
  known MVP limitation: single-instance only, unbounded key growth over a long
  uptime. A multi-instance deployment would need a shared store (Redis, Postgres)
  instead — documented here rather than silently accepted as invisible debt.
- The manual SQLi/XSS/CSRF/SSRF/container review found no active vulnerabilities
  to fix — see docs/security-audit-phase9.md for the actual findings and why. A
  clean review is still a real deliverable: it's evidence the architecture's
  existing choices (parameterized JPQL everywhere, React's default escaping, a
  redirect-only Open Banking model, non-root multi-stage Docker builds) are
  doing their job, not an absence of effort.
