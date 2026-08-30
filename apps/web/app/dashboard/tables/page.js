"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";

const BLANK_ROW = { description: "", amount: "" };

export default function TablesSetupPage() {
  const [venues, setVenues] = useState([]);
  const [selectedVenueId, setSelectedVenueId] = useState(null);
  const [tables, setTables] = useState([]);
  const [error, setError] = useState(null);

  const [venueName, setVenueName] = useState("");
  const [tableLabel, setTableLabel] = useState("");
  const [billTable, setBillTable] = useState(null);
  const [existingBill, setExistingBill] = useState(null);
  const [lineItems, setLineItems] = useState([]);

  useEffect(() => {
    loadVenues();
  }, []);

  useEffect(() => {
    if (selectedVenueId) loadTables(selectedVenueId);
  }, [selectedVenueId]);

  async function loadVenues() {
    try {
      const result = await apiFetch("/api/v1/venues");
      setVenues(result);
      if (result.length > 0 && !selectedVenueId) setSelectedVenueId(result[0].id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load venues.");
    }
  }

  async function loadTables(venueId) {
    try {
      setTables(await apiFetch(`/api/v1/venues/${venueId}/tables`));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load tables.");
    }
  }

  async function createVenue(e) {
    e.preventDefault();
    try {
      const venue = await apiFetch("/api/v1/venues", { method: "POST", body: JSON.stringify({ name: venueName }) });
      setVenueName("");
      await loadVenues();
      setSelectedVenueId(venue.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create venue.");
    }
  }

  async function createTable(e) {
    e.preventDefault();
    try {
      await apiFetch(`/api/v1/venues/${selectedVenueId}/tables`, { method: "POST", body: JSON.stringify({ label: tableLabel }) });
      setTableLabel("");
      loadTables(selectedVenueId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create table.");
    }
  }

  function toEditableBill(bill) {
    return { ...bill, lineItems: bill.lineItems.map((li) => ({ ...li, amountDisplay: (li.amountMinorUnits / 100).toFixed(2), dirty: false })) };
  }

  async function openBillPanel(table) {
    setBillTable(table);
    setExistingBill(null);
    if (table.occupancyStatus === "OCCUPIED") {
      setLineItems([{ ...BLANK_ROW }]);
      try {
        const scan = await apiFetch(`/api/v1/tables/scan/${table.qrToken}`);
        setExistingBill(toEditableBill(scan.bill));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Failed to load the current bill.");
      }
    } else {
      setLineItems([
        { description: "Food", amount: "" },
        { description: "Drinks", amount: "" },
        { description: "Dessert", amount: "" },
      ]);
    }
  }

  function updateExistingItemField(itemId, field, value) {
    setExistingBill((bill) => ({
      ...bill,
      lineItems: bill.lineItems.map((li) => (li.id === itemId ? { ...li, [field]: value, dirty: true } : li)),
    }));
  }

  async function commitExistingItem(item) {
    if (!item.dirty) return;
    const amountMinorUnits = Math.round(parseFloat(item.amountDisplay) * 100);
    if (!item.description.trim() || !Number.isFinite(amountMinorUnits) || amountMinorUnits <= 0) return;
    try {
      const updated = await apiFetch(`/api/v1/bills/${existingBill.id}/items/${item.id}`, {
        method: "PATCH",
        body: JSON.stringify({ description: item.description.trim(), amountMinorUnits }),
      });
      setExistingBill(toEditableBill(updated));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save the change.");
    }
  }

  async function removeExistingItem(item) {
    try {
      const updated = await apiFetch(`/api/v1/bills/${existingBill.id}/items/${item.id}`, { method: "DELETE" });
      setExistingBill(toEditableBill(updated));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to remove the item.");
    }
  }

  function closeBillPanel() {
    setBillTable(null);
    setExistingBill(null);
  }

  function addLineItemRow() {
    setLineItems((items) => [...items, { ...BLANK_ROW }]);
  }

  function removeLineItemRow(index) {
    setLineItems((items) => items.filter((_, j) => j !== index));
  }

  async function submitBillItems(e) {
    e.preventDefault();
    try {
      const items = lineItems
        .filter((li) => li.description && li.amount)
        .map((li) => ({ description: li.description, amountMinorUnits: Math.round(parseFloat(li.amount) * 100) }));
      const path = billTable.occupancyStatus === "OCCUPIED"
        ? `/api/v1/tables/${billTable.id}/bill/items`
        : `/api/v1/tables/${billTable.id}/bill`;
      await apiFetch(path, { method: "POST", body: JSON.stringify({ lineItems: items }) });
      closeBillPanel();
      loadTables(selectedVenueId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save the bill.");
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>ScanSettle Tables</h1>
      {error && <div className="error-banner">{error}</div>}

      <div style={{ display: "flex", gap: 24 }}>
        <div style={{ width: 280 }}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8 }}>Venues</div>
          {venues.map((v) => (
            <div key={v.id} onClick={() => setSelectedVenueId(v.id)}
                 className="card" style={{ padding: 10, marginBottom: 6, cursor: "pointer", borderColor: v.id === selectedVenueId ? "var(--color-accent)" : "var(--color-line)" }}>
              {v.name}
            </div>
          ))}
          <form onSubmit={createVenue} style={{ display: "flex", gap: 6, marginTop: 8 }}>
            <input placeholder="New venue name" value={venueName} onChange={(e) => setVenueName(e.target.value)}
                   style={{ flex: 1, border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
            <button className="btn btn-secondary" type="submit">Add</button>
          </form>
        </div>

        {selectedVenueId && (
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8 }}>Tables</div>
            <div className="card" style={{ overflow: "auto", marginBottom: 12 }}>
              <table className="data-table">
                <thead><tr><th>Label</th><th>Status</th><th>QR link</th><th></th></tr></thead>
                <tbody>
                  {tables.map((t) => (
                    <tr key={t.id}>
                      <td style={{ fontWeight: 600 }}>{t.label}</td>
                      <td>
                        <span className={`badge ${t.occupancyStatus === "OCCUPIED" ? "badge-warn" : "badge-good"}`}>
                          {t.occupancyStatus === "OCCUPIED" ? "Occupied" : "Free"}
                        </span>
                      </td>
                      <td className="mono" style={{ fontSize: 12 }}>{`${typeof window !== "undefined" ? window.location.origin : ""}/tables/${t.qrToken}`}</td>
                      <td>
                        <button className="btn btn-secondary" onClick={() => openBillPanel(t)}>
                          {t.occupancyStatus === "OCCUPIED" ? "Add items" : "Open bill"}
                        </button>
                      </td>
                    </tr>
                  ))}
                  {tables.length === 0 && <tr><td colSpan={4} style={{ color: "var(--color-muted)" }}>No tables yet.</td></tr>}
                </tbody>
              </table>
            </div>
            <form onSubmit={createTable} style={{ display: "flex", gap: 6 }}>
              <input placeholder="New table label, e.g. Table 14" value={tableLabel} onChange={(e) => setTableLabel(e.target.value)}
                     style={{ flex: 1, border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
              <button className="btn btn-secondary" type="submit">Add table</button>
            </form>

            {billTable && (
              <form onSubmit={submitBillItems} className="card" style={{ padding: 16, marginTop: 20 }}>
                <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 10 }}>
                  {billTable.occupancyStatus === "OCCUPIED"
                    ? `Add items to ${billTable.label}'s running bill`
                    : `Open bill — line items`}
                </div>

                {billTable.occupancyStatus === "OCCUPIED" && existingBill && (
                  <div style={{ background: "var(--color-bg)", borderRadius: 6, padding: 10, marginBottom: 14 }}>
                    <div style={{ fontSize: 11, fontWeight: 700, color: "var(--color-muted)", textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 6 }}>
                      Current bill — click to amend
                    </div>
                    {existingBill.lineItems.map((li) => (
                      <div key={li.id} style={{ display: "flex", gap: 8, marginBottom: 6, alignItems: "center" }}>
                        <input value={li.description}
                               onChange={(e) => updateExistingItemField(li.id, "description", e.target.value)}
                               onBlur={() => commitExistingItem(li)}
                               onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); e.currentTarget.blur(); } }}
                               style={{ flex: 2, border: "1px solid var(--color-line)", borderRadius: 6, padding: "6px 8px", fontSize: 13, background: "var(--color-panel)" }} />
                        <input value={li.amountDisplay} inputMode="decimal"
                               onChange={(e) => updateExistingItemField(li.id, "amountDisplay", e.target.value)}
                               onBlur={() => commitExistingItem(li)}
                               onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); e.currentTarget.blur(); } }}
                               style={{ flex: 1, border: "1px solid var(--color-line)", borderRadius: 6, padding: "6px 8px", fontSize: 13, background: "var(--color-panel)" }} />
                        <button type="button" className="btn btn-secondary" disabled={existingBill.lineItems.length <= 1}
                                onClick={() => removeExistingItem(li)} aria-label="Remove item" style={{ padding: "6px 10px" }}>
                          ×
                        </button>
                      </div>
                    ))}
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, fontWeight: 700, borderTop: "1px solid var(--color-line)", marginTop: 6, paddingTop: 6 }}>
                      <span>Total so far</span>
                      <span className="mono">{formatMinorUnits(existingBill.totalAmountMinorUnits, existingBill.currencyCode)}</span>
                    </div>
                    {existingBill.paidAmountMinorUnits > 0 && (
                      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--color-muted)", marginTop: 2 }}>
                        <span>Already paid</span>
                        <span className="mono">{formatMinorUnits(existingBill.paidAmountMinorUnits, existingBill.currencyCode)}</span>
                      </div>
                    )}
                  </div>
                )}

                <div style={{ fontSize: 11, fontWeight: 700, color: "var(--color-muted)", textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 6 }}>
                  {billTable.occupancyStatus === "OCCUPIED" ? "New items" : "Line items"}
                </div>
                {lineItems.map((li, i) => (
                  <div key={i} style={{ display: "flex", gap: 8, marginBottom: 8 }}>
                    <input placeholder="Description" value={li.description}
                           onChange={(e) => setLineItems((items) => items.map((it, j) => j === i ? { ...it, description: e.target.value } : it))}
                           style={{ flex: 2, border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
                    <input placeholder="Amount (£)" inputMode="decimal" value={li.amount}
                           onChange={(e) => setLineItems((items) => items.map((it, j) => j === i ? { ...it, amount: e.target.value } : it))}
                           style={{ flex: 1, border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
                    {lineItems.length > 1 && (
                      <button type="button" className="btn btn-secondary" onClick={() => removeLineItemRow(i)}
                              aria-label="Remove item" style={{ padding: "8px 12px" }}>
                        ×
                      </button>
                    )}
                  </div>
                ))}
                <button type="button" className="btn btn-secondary" onClick={addLineItemRow} style={{ marginBottom: 10 }}>
                  + Add another item
                </button>

                <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
                  <button className="btn" type="submit">
                    {billTable.occupancyStatus === "OCCUPIED" ? "Add items" : "Open bill"}
                  </button>
                  <button className="btn btn-secondary" type="button" onClick={closeBillPanel}>Cancel</button>
                </div>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
