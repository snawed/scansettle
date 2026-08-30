# ScanSettle — Open Banking Abstraction (Phase 0)

Status: DRAFT — awaiting Product Owner approval

## 1. Interface (domain-facing, provider-agnostic)

```
interface OpenBankingProvider {
    AuthorisationResult createAuthorisation(PaymentInstruction instruction);
    ProviderPayment createPayment(PaymentInstruction instruction);
    PaymentStatusResult getPaymentStatus(String providerReference);
    void cancelPayment(String providerReference);
    RefundResult refundPayment(String providerReference, Money amount); // see below
    WebhookProcessingResult handleWebhook(WebhookRequest request);
    List<SupportedBank> getSupportedBanks();
}
```

All types in this interface (`PaymentInstruction`, `ProviderPayment`,
`PaymentStatusResult`, `SupportedBank`, `WebhookRequest`, `WebhookProcessingResult`)
are **ScanSettle domain types**, not provider SDK types. Every real adapter
(`{{OPEN_BANKING_PROVIDER}}Adapter`) is responsible for translating to/from the
vendor's own request/response/webhook shapes internally; nothing vendor-specific
crosses the interface boundary. This is enforced by contract tests written against
the interface (Phase 2/4), which both `MockOpenBankingProvider` and any real adapter
must pass identically.

Note on `refundPayment`: given the regulatory reality (see architecture.md Section 8,
ADR-0004), this method exists on the interface for providers that *do* offer a
payout/refund capability, but ScanSettle's domain layer does not assume it is always
available — `Refund` requests default to a manual/tracked workflow unless a given
adapter reports refund support via its capability flags.

## 2. Configuration (placeholders, never hardcoded)

```
OPEN_BANKING_PROVIDER=          # e.g. "mock" for MVP; real provider name from Phase 5
OPEN_BANKING_CLIENT_ID={{OPEN_BANKING_CLIENT_ID}}
OPEN_BANKING_CLIENT_SECRET={{OPEN_BANKING_CLIENT_SECRET}}
OPEN_BANKING_WEBHOOK_SECRET={{OPEN_BANKING_WEBHOOK_SECRET}}
OPEN_BANKING_REDIRECT_URL={{OPEN_BANKING_REDIRECT_URL}}
```

Provider selection is a Spring `@ConditionalOnProperty`-style bean choice — swapping
`OPEN_BANKING_PROVIDER` swaps the adapter with no core code change. Secrets are never
committed; local dev uses `.env`/docker-compose secrets, non-local environments use a
secrets manager (mechanism TBD at Phase 10, cloud-agnostic for now).

## 3. MVP behaviour

Phase 2–4 build and exercise `MockOpenBankingProvider` only — a fully working fake
that supports configurable outcomes (approve, reject, timeout, delayed webhook,
duplicate webhook) so the entire payment journey and its failure modes can be
demonstrated and tested before any real provider is chosen. Phase 5 adds a real
adapter only once `{{OPEN_BANKING_PROVIDER}}` is explicitly specified and approved,
and only against a sandbox/test environment until explicitly told otherwise.
