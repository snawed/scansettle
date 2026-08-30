"use client";

import { useState } from "react";
import { opsFetch } from "@/lib/opsApiClient";
import { ApiError } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";

export default function OpsInvestigatePage() {
  const [paymentId, setPaymentId] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSearch(e) {
    e.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      setResult(await opsFetch(`/api/v1/admin/payments/${paymentId}/investigate`));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to investigate this payment.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>Investigate Payment</h1>
      {error && <div className="error-banner">{error}</div>}

      <form onSubmit={handleSearch} style={{ display: "flex", gap: 8, marginBottom: 20 }}>
        <input placeholder="Payment ID" value={paymentId} onChange={(e) => setPaymentId(e.target.value)}
               style={{ width: 360, border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
        <button className="btn" type="submit" disabled={loading}>{loading ? "Searching…" : "Investigate"}</button>
      </form>

      {result && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <Section title="Payment">
            <Row label="ID" value={result.payment.id} mono />
            <Row label="Merchant" value={result.payment.merchantTradingName} />
            <Row label="Amount" value={formatMinorUnits(result.payment.amountMinorUnits, result.payment.currencyCode)} />
            <Row label="State" value={result.payment.state} />
            <Row label="Created" value={new Date(result.payment.createdAt).toLocaleString()} />
            <Row label="Last updated" value={new Date(result.payment.updatedAt).toLocaleString()} />
          </Section>

          <Section title="Provider transaction">
            {result.providerTransaction ? (
              <>
                <Row label="Provider" value={result.providerTransaction.provider} />
                <Row label="Provider reference" value={result.providerTransaction.providerReference} mono />
                <Row label="Raw status" value={result.providerTransaction.rawStatus} />
                <Row label="Last synced" value={new Date(result.providerTransaction.lastSyncedAt).toLocaleString()} />
              </>
            ) : (
              <div style={{ color: "var(--color-muted)", fontSize: 13 }}>No provider transaction — this payment never reached the bank.</div>
            )}
          </Section>

          <Section title={`Webhook history (${result.webhookEvents.length})`}>
            {result.webhookEvents.length === 0 && (
              <div style={{ color: "var(--color-muted)", fontSize: 13 }}>No webhooks received for this payment.</div>
            )}
            {result.webhookEvents.map((w) => (
              <div key={w.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, padding: "4px 0", borderBottom: "1px solid var(--color-line)" }}>
                <span>{new Date(w.receivedAt).toLocaleString()}</span>
                <span>{w.signatureValid ? "Valid signature" : "Invalid signature"}</span>
                <span className="mono">{w.processingResult || "—"}</span>
              </div>
            ))}
          </Section>

          <Section title="Reconciliation">
            {result.reconciliation.length === 0 && (
              <div style={{ color: "var(--color-muted)", fontSize: 13 }}>No reconciliation record yet — payment hasn't reached a terminal state.</div>
            )}
            {result.reconciliation.map((r) => (
              <div key={r.id} style={{ fontSize: 13, padding: "4px 0" }}>
                <Row label="Expected" value={formatMinorUnits(r.expectedAmountMinorUnits, result.payment.currencyCode)} />
                <Row label="Confirmed" value={r.confirmedAmountMinorUnits != null ? formatMinorUnits(r.confirmedAmountMinorUnits, result.payment.currencyCode) : "—"} />
                <Row label="Matched" value={r.matched ? "Yes" : "No"} />
                {r.discrepancyNote && <Row label="Note" value={r.discrepancyNote} />}
              </div>
            ))}
          </Section>
        </div>
      )}
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="card" style={{ padding: 16 }}>
      <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 10 }}>{title}</div>
      {children}
    </div>
  );
}

function Row({ label, value, mono }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, padding: "4px 0" }}>
      <span style={{ color: "var(--color-muted)" }}>{label}</span>
      <span className={mono ? "mono" : undefined}>{value}</span>
    </div>
  );
}
