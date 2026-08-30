# ScanSettle Tables — Concurrency Design (Phase 0)

Status: DRAFT — awaiting Product Owner approval

## 1. The problem (from the brief, Section 23)

Bill remaining = £50. Customer A attempts £40, Customer B simultaneously attempts £30.
The two must not both be allowed to succeed (£70 > £50 remaining), unless the business
explicitly supports overpayment (it does not, for MVP).

## 2. Design: reservation pattern

A payment attempt against a bill does not touch `remainingBalance` directly and does
not wait until the customer finishes bank authentication to be accounted for. Instead:

1. Customer enters an amount (full/split/custom) and taps "Pay by Bank".
2. ScanSettle opens a **database transaction**, takes a row lock on the `Bill` (or
   uses an optimistic `version` column — see below), and checks:
   `requestedAmount <= Bill.total − Σ(committed BillPayments) − Σ(other ACTIVE reservations)`.
3. If it fits, a `BillPaymentReservation` is inserted (status `ACTIVE`, an
   `expiresAt` a few minutes ahead — matched to a realistic bank-auth session length,
   e.g. 10 minutes) **in the same transaction**, and the transaction commits. Only
   then does ScanSettle call the Open Banking provider to start the redirect.
4. If it doesn't fit, the request is rejected immediately, before any bank redirect —
   the customer sees "Remaining balance has changed — please refresh" rather than
   going through a bank flow that would fail anyway.
5. On provider confirmation (`PAYMENT_CONFIRMED`), the reservation is **committed**:
   a `BillPayment` is created/updated to reflect it, the reservation moves to
   `COMMITTED`, and the bill's committed total increases accordingly.
6. On failure/rejection/cancellation, the reservation moves to `RELEASED` immediately,
   freeing the amount for others.
7. A scheduled sweep releases any reservation still `ACTIVE` past `expiresAt`
   (abandoned bank flow) — status becomes `EXPIRED`, amount freed.

### Why a pessimistic row lock (not just optimistic concurrency)

The critical section (check-remaining, insert-reservation) does **no external calls**
— it's a single short database transaction. A `SELECT ... FOR UPDATE` on the `Bill`
row for that brief moment is cheap and simple, and table-side concurrency (a handful
of diners) is orders of magnitude below anything that would make row-lock contention a
real bottleneck. This is deliberately simpler than optimistic retry-loops for the
expected load; documented as a decision to revisit only if data shows contention.

### Worked example (from the brief)

Bill = £120, remaining £120.

- Customer A requests £40 → check: 40 ≤ 120 ✓ → reservation A (£40) created,
  remaining-for-others now £80.
- Customer B requests £30 (concurrently) → check: 30 ≤ 80 ✓ → reservation B (£30)
  created, remaining-for-others now £50.
- Customer C requests £50 (concurrently) → check: 50 ≤ 50 ✓ → reservation C (£50)
  created, remaining-for-others now £0.
- All three proceed to their bank. Suppose all three confirm: reservations A, B, C
  each commit to a `BillPayment`; committed total = £120 = bill total → `Bill.state`
  → `PAID`.
- If Customer A had instead abandoned their bank flow, reservation A expires after
  its TTL, freeing £40 back — B and C are unaffected since they already locked their
  own amounts at request time.

### Sequence diagram

```mermaid
sequenceDiagram
    actor A as Customer A
    actor B as Customer B
    participant SS as ScanSettle (Tables module)
    participant DB as PostgreSQL
    participant OB as {{OPEN_BANKING_PROVIDER}}

    A->>SS: Pay £40 against Bill (£50 remaining)
    SS->>DB: BEGIN; SELECT bill FOR UPDATE; check 40<=50
    DB-->>SS: OK
    SS->>DB: INSERT reservation A (£40, ACTIVE); COMMIT
    SS->>OB: createPayment(£40)
    OB-->>A: redirect to bank

    B->>SS: Pay £30 against Bill (now £10 remaining for others)
    SS->>DB: BEGIN; SELECT bill FOR UPDATE; check 30<=10
    DB-->>SS: fails check
    SS-->>B: 409 — amount exceeds remaining balance, refresh

    OB-->>SS: webhook: Payment A CONFIRMED
    SS->>DB: reservation A -> COMMITTED, BillPayment created, Bill totals updated
```

## 3. Tipping

Tip is captured as part of the *same* reservation/payment amount — the customer picks
a tip (none/5%/10%/custom) before confirming, and the reservation is created for
`contributionAmount + tipAmount` (the full amount that will be authorised at the
customer's bank). `BillPayment.tipAmount` is stored separately from
`contributionAmount` so reporting/reconciliation can distinguish "paid off the bill"
from "gratuity" even though both were authorised in a single bank payment. Tips reduce
the *reservation* the same way a normal contribution does (a tip isn't "free" capacity
— it's still money the customer is committing in that authorisation, so it's tracked
for reporting but does not reduce `remainingBalance`, since the bill's remaining
balance is about the bill total, not gratuity).

## 4. Test scenarios required in Phase 6

- Two customers paying simultaneously, both within remaining balance → both succeed.
- Two customers paying simultaneously where the sum exceeds remaining balance → one
  succeeds, one is rejected pre-redirect.
- Failed payment after a reservation was created → balance correctly released.
- Abandoned payment (reservation TTL expiry) → balance correctly released after sweep.
- Successful equal split (e.g. 3-way split of £120) → bill reaches `PAID` with no
  over/under-collection.
- Tip calculation correctness across NONE/5%/10%/custom, and correct separation of
  `contributionAmount` vs `tipAmount` in reporting.
