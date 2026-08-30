# ADR-0007: Webhook ingestion is a shared in-process service, not a self-HTTP call

Status: Accepted
Date: 2026-08-30

## Context

Phase 4 needed a real webhook path (signature verification, idempotency, replay
protection) plus a way to demonstrate the full customer journey (redirect to a bank,
approve/decline, land back on ScanSettle) without a real Open Banking provider. The
natural design was a "mock bank" page whose Approve/Decline action fires the same
webhook a real bank/provider round-trip would produce.

The first implementation had the mock-bank decision endpoint make a real HTTP call to
ScanSettle's own public webhook endpoint (`localhost:{server.port}`), to prove the
webhook path was genuinely exercised rather than shortcut. This broke under
`@SpringBootTest(webEnvironment = RANDOM_PORT)`: `@Value("${server.port}")` resolves
to the *configured* port, not the *actual* randomly-assigned one Spring Boot binds to
in that test mode — the self-call connected to the wrong port and failed
(`Can't assign requested address`).

## Decision

Extracted the webhook verification/deduplication/application logic out of the HTTP
controller into `WebhookIngestionService`. The real public endpoint
(`OpenBankingWebhookController`) is now a thin HTTP adapter over it; the mock-bank
decision endpoint calls the same service method directly, in-process.

## Consequences

- No fragile self-HTTP loopback, in any environment (test, dev, prod-shaped
  container) — one less thing that can break for reasons unrelated to the feature
  being tested.
- Signature verification and idempotency are still exercised exactly as before — the
  HMAC signing and verification both happen in this process regardless of transport,
  so nothing about the coverage was lost by removing the network hop.
- Cleaner separation of concerns as a side effect: `OpenBankingWebhookController` is
  now genuinely thin, and the core logic is unit-testable without MockMvc if ever
  needed.

## Alternatives considered

- Reading the actual bound port via `@LocalServerPort` / `local.server.port` instead
  of `server.port` — rejected: that property only exists in a Spring Boot test
  context, so production code would carry a test-only special case for no benefit
  over just not doing an HTTP self-call at all.
