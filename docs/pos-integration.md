# ScanSettle — POS Abstraction (Phase 0)

Status: DRAFT — awaiting Product Owner approval

## 1. Interface

```
interface POSProvider {
    TableInfo getTable(String posTableRef);
    BillInfo getBill(String posBillRef);
    void updatePayment(String posBillRef, PaymentUpdate update);
    void closeBill(String posBillRef);
    WebhookProcessingResult handleWebhook(WebhookRequest request);
}
```

As with `OpenBankingProvider`, all types are ScanSettle domain types
(`TableInfo`, `BillInfo`, `PaymentUpdate`). Provider-specific implementation lives
entirely in `{{POS_PROVIDER}}Adapter`.

## 2. Configuration (placeholders)

```
POS_PROVIDER=                 # e.g. "mock" for MVP
POS_API_URL={{POS_API_URL}}
POS_CLIENT_ID={{POS_CLIENT_ID}}
POS_CLIENT_SECRET={{POS_CLIENT_SECRET}}
```

## 3. MVP behaviour

ScanSettle Tables must work fully with **no real POS connected** — a venue can be set
up, tables created, and bills opened/managed directly within ScanSettle
(`MockPOSProvider`, or in practice a manual/merchant-entry path for line items and
totals) so the hospitality product is demonstrable end-to-end before any POS
integration exists. This also reflects that many independent pubs/cafés targeted in
the MVP may not have (or need) a POS integration at all — POS integration is an
enhancement for venues that already run a supported POS, not a hard dependency of the
product.

`{{POS_PROVIDER}}` integration (Phase 7) is additive: once connected, `Bill` and
`BillLineItem` data can be sourced from the POS instead of manual entry, and
`closeBill()` can push settlement back to the till — but the core Tables payment flow
(reservation, split, tip, concurrency) is identical either way, since it is designed
entirely at the ScanSettle domain layer, not the POS layer.
