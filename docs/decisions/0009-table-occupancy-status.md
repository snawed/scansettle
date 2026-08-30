# ADR-0009: Table occupancy is a first-class field, not derived from bill state

Status: Accepted
Date: 2026-08-30

## Context

Once a `Bill` reached `PAID`, the table itself kept showing that bill's numbers to
the next QR scan until a member of staff opened a fresh bill — there was no signal
that the table was actually free again. The Product Owner asked for this to behave
"like how a POS records" a table: paid means free, and a free table shouldn't keep
serving the old bill's data.

The alternative to a stored field would have been deriving "is this table free"
on every scan by checking whether the latest bill for the table is in a terminal
state. That works, but it conflates two different questions — "is there an open
bill" (a query over `Bill`) and "is this table available for a new one" (what the
front-of-house actually needs to see at a glance) — and it would leave the merchant
table list unable to show a live FREE/OCCUPIED badge without re-deriving it per row
on every render.

## Decision

`DiningTable` gets its own `occupancyStatus` (`FREE`/`OCCUPIED`) column, set
explicitly at the two points that actually change it: `BillController.openBill`
sets `OCCUPIED`; `BillController.voidBill` and `BillPaymentOutcomeListener` (when
`Bill.reflectCommittedTotal` brings the bill to `PAID`) set `FREE`. This is
distinct from the pre-existing `DiningTable.status` (`ACTIVE`/`INACTIVE`), which
means "is this table enabled in the system at all" — an unrelated concept.

`GET /tables/scan/{qrToken}` checks `occupancyStatus` first: `FREE` returns
`{occupancyStatus: "FREE", bill: null}` instead of walking the bill history, so a
customer who scans a settled table sees "no bill open — ask a member of staff"
rather than a stale paid bill. The merchant table list (`GET
/venues/{id}/tables`) now returns `occupancyStatus` too, so the dashboard can show
a live badge and disable "Open bill" on an occupied table without a second call.

## Consequences

- Reopening a bill on a previously-`PAID` table (already allowed, since
  `isOpenForPayment()` only blocks on `OPEN`/`PARTIALLY_PAID`) now also flips the
  table back to `OCCUPIED` — no separate trigger needed since it goes through the
  same `openBill` path.
- The historical `Bill` row is untouched and still queryable for reconciliation —
  only what a *new* scan is served changes.
- New Flyway migration V4 adds the column with a `FREE` default, so existing rows
  don't need backfilling logic.

## Addendum: adding items to a running tab (2026-08-30)

A table staying `OCCUPIED` naturally raised the next question: a real tab grows
over the course of a sitting (more drinks, another course), so the venue needs to
add line items to the *same* open bill rather than being forced to void and reopen
one bill per round.

`POST /tables/{tableId}/bill/items` (STAFF+) finds the table's current
`isOpenForPayment()` bill, appends the new `BillLineItem` rows, and calls
`Bill.addToTotal(...)` — row-locked via the same `findByIdForUpdate` used by the
reservation critical section (ADR-0003), so two staff adding items to the same bill
at once can't lose an update. It rejects with 409 if the table has no open bill
(i.e. it's `FREE`) — items only ever land on a running tab, never resurrect a
settled one. Because the total only ever increases, this can't create the
overpayment condition the reservation pattern guards against: any in-flight
reservation's balance check already happened against a total that was at most the
new, larger one.

The merchant Tables UI reuses the same line-item form for both actions — its
label and target endpoint switch on the table's `occupancyStatus` ("Open bill" vs.
"Add items"), so front-of-house doesn't need a separate screen for the common case
of a table running a tab across a whole sitting.

## Addendum: amending and removing existing items (2026-08-30)

Adding items covers the forward case, but front-of-house also needs to fix a
mis-entered price or description, or remove an item rung up on the wrong table —
without voiding the whole bill and starting over.

`PATCH /bills/{billId}/items/{itemId}` and `DELETE /bills/{billId}/items/{itemId}`
(both STAFF+) do this. Both reject with 409 if the bill isn't `isOpenForPayment()`
— a settled or voided bill's items are a historical record, not editable. Both
recompute `Bill.totalAmountMinorUnits` as the sum of all remaining line items
(`BillController.recalculateTotalFromLineItems`) rather than applying a delta —
the same recompute-from-source approach the "add items" endpoint was refactored to
use, so total drift can't accumulate across a mix of adds, amends, and removals on
the same bill. Delete additionally refuses to remove a bill's last remaining item
(409) — a zero-item, zero-total bill would trivially read as fully paid without a
Payment ever existing, which is confusing enough to be worth a one-line guard;
voiding the bill is the correct move once there's nothing left to charge for.

The Tables dashboard's "Current bill" panel became directly editable: each
existing item is a live description/amount pair that PATCHes on blur and a ×
button that DELETEs immediately, with the total recalculating from whatever the
endpoint returns. The remove button disables itself once only one item remains,
mirroring the backend guard rather than relying on the customer seeing a 409.
