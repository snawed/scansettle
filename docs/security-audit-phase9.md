# ScanSettle — Phase 9 Security Audit

Status: Complete for MVP scope — see ADR-0011 for what this closed vs. deferred.
Date: 2026-08-30

This is the "full OWASP review, dependency vulnerability scanning, penetration-test
checklist, targeted SSRF/CSRF/XSS/SQLi review, container security" pass docs/security.md
Section 4 scoped to Phase 9. Each area below is a real finding from reading the
actual code and running what tooling this environment can, not a checklist
rubber-stamp — where a live scan would do better in a real deployment, that's said
plainly rather than faked.

## SQL Injection

**Finding: no risk found.** Every query in the codebase is either a Spring Data
derived query (method name → generated JPQL, fully parameterized) or an explicit
`@Query` using named parameters (`:id`, `:merchantId`, etc.) — grepped the whole
`src/main/java` tree for `@Query`, `createNativeQuery`, `nativeQuery = true`, and
any string concatenation resembling hand-built SQL (`"select ` etc. outside an
`@Query` annotation). Zero native queries, zero string-concatenated query text
anywhere. Hibernate/JPA's parameter binding is the only path data ever reaches the
database through.

## Cross-Site Scripting (XSS)

**Finding: no risk found.** The backend is a pure JSON API — it never renders
HTML, so there's no server-side templating injection surface. The frontend is
React (Next.js App Router), which escapes all interpolated JSX content by default;
grepped `apps/web` for `dangerouslySetInnerHTML`, `.innerHTML =`, `eval(`, and
`new Function(` — none present anywhere in `app/` or `lib/`.

## Cross-Site Request Forgery (CSRF)

**Finding: not applicable by design, not missing.** CSRF exploits a browser's
automatic attachment of cookies to a cross-origin request; ScanSettle's auth is a
bearer token in the `Authorization` header, read from `localStorage`, which a
cross-origin attacker page cannot read or set without already having compromised
the origin (at which point CSRF is the least of the problem). `SecurityConfig`
disables Spring Security's CSRF protection explicitly, with this reasoning now in
a code comment at the disable site, not just this document.

## Server-Side Request Forgery (SSRF)

**Finding: no risk found — structurally, not just by convention.** Grepped for
any outbound HTTP client (`RestTemplate`, `WebClient`, `HttpClient`,
`URLConnection`) anywhere in `src/main/java`: none exist. The backend never makes
an outbound HTTP call of any kind. The Open Banking "redirect" model means
`redirectUrl` values are generated server-side and handed to the *customer's
browser* to navigate to — the server never dereferences a URL itself, so there is
no code path where user input could steer a server-side fetch even in principle.

## Container security

**Finding: already solid, no changes needed.** Both `apps/api/Dockerfile` and
`apps/web/Dockerfile`:
- Multi-stage builds — build toolchains (Maven, npm, the JDK/Node dev images)
  never ship in the final image.
- Run as a dedicated non-root user (`scansettle`), not root.
- Use minimal base images (`eclipse-temurin:21-jre-alpine`, `node:20-alpine`).
- Take no secrets as build-time `ARG`/`ENV` with real values — `infra/docker-compose.yml`'s
  secrets are all explicitly-labeled dev-only placeholders, overridable via
  environment variables for a real deployment, never hardcoded into the image
  itself.

## Dependency vulnerabilities

**`npm audit --omit=dev` (apps/web): 0 vulnerabilities** as of this audit — ran
directly, real result, not aspirational.

**Backend (Maven): manually reviewed, not live-scanned.** This sandboxed
environment has no network path to the NVD/OSS Index databases a real
`org.owasp:dependency-check-maven` or Snyk run needs, so a live CVE scan against
current advisories isn't something that could be run here honestly. Direct
dependencies were reviewed by version: Spring Boot 3.3.4 (parent BOM, pulls in
patched transitive versions of every Spring component), `jjwt` 0.12.6, `zxing`
3.5.3 — all reasonably current, nothing flagged from general knowledge of this
dependency set. **This is the one area where "the review found nothing" carries
the least confidence** — a live scan really is the only trustworthy answer here.
Closed the gap the honest way: wired `.github/dependabot.yml` (Maven, npm, Docker,
GitHub Actions — weekly) and an `npm audit --omit=dev --audit-level=high` CI step
so real, current scanning runs continuously going forward, on infrastructure that
actually has network access.

## Fraud/velocity, rate limiting, step-up auth, MFA

Not a "review found nothing" item — these were genuine gaps, closed as concrete
Phase 9 work. See ADR-0011 for what got built (`RateLimitFilter`, `RateLimiter`,
`PaymentVelocityGuard`, `VelocityTracker`, bank-account step-up re-auth, ops MFA)
and why.

## Known limitations carried forward, not silently accepted

- `RateLimiter` and `VelocityTracker` are both single-instance, in-process,
  unbounded-key-growth structures — correct for the MVP's single-instance
  deployment (architecture.md Section 12), but a multi-instance deployment would
  need a shared store (Redis, Postgres) instead of these exact classes.
- No JWT revocation for either merchant or ops tokens — a token keeps working
  until it expires even if the account is suspended/compromised in the interim
  (merchant suspension is checked at *login* time, closing the "suspend does
  nothing" gap from Phase 8, but an already-issued token from before suspension
  isn't invalidated). Real revocation needs either short-lived tokens + refresh,
  or a server-side denylist — both are real infrastructure additions, not a
  one-line fix, and are flagged here rather than silently deferred.
- Ops-account provisioning is still just the one Flyway-seeded dev account — no
  self-registration (correctly, since anyone self-registering as ops would defeat
  the whole point) and no "invite another ops user" flow either. ADR-0010 already
  flagged this; still open.
- The dependency-vulnerability finding above is a manual review, not a live scan
  — treat it as weaker evidence than everything else in this document until CI
  actually runs the Dependabot/`npm audit` checks that are now wired in.
