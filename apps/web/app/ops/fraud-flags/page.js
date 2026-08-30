"use client";

import { useEffect, useState } from "react";
import { opsFetch } from "@/lib/opsApiClient";
import { ApiError } from "@/lib/apiClient";

export default function OpsFraudFlagsPage() {
  const [flags, setFlags] = useState([]);
  const [error, setError] = useState(null);
  const [targetType, setTargetType] = useState("merchant");
  const [targetId, setTargetId] = useState("");
  const [reason, setReason] = useState("");

  useEffect(() => {
    load();
  }, []);

  async function load() {
    try {
      setFlags(await opsFetch("/api/v1/admin/fraud-flags"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load fraud flags.");
    }
  }

  async function raise(e) {
    e.preventDefault();
    try {
      const body = targetType === "merchant"
        ? { merchantId: targetId, reason }
        : { paymentId: targetId, reason };
      await opsFetch("/api/v1/admin/fraud-flags", { method: "POST", body: JSON.stringify(body) });
      setTargetId("");
      setReason("");
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to raise fraud flag.");
    }
  }

  async function clear(id) {
    try {
      await opsFetch(`/api/v1/admin/fraud-flags/${id}/clear`, { method: "POST" });
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to clear fraud flag.");
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>Fraud Flags</h1>
      {error && <div className="error-banner">{error}</div>}

      <form onSubmit={raise} className="card" style={{ padding: 16, marginBottom: 20, display: "flex", gap: 8, alignItems: "flex-end" }}>
        <div className="field" style={{ margin: 0 }}>
          <label>Target</label>
          <select value={targetType} onChange={(e) => setTargetType(e.target.value)}
                  style={{ border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }}>
            <option value="merchant">Merchant</option>
            <option value="payment">Payment</option>
          </select>
        </div>
        <div className="field" style={{ margin: 0, flex: 1 }}>
          <label>{targetType === "merchant" ? "Merchant ID" : "Payment ID"}</label>
          <input required value={targetId} onChange={(e) => setTargetId(e.target.value)}
                 style={{ width: "100%", border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
        </div>
        <div className="field" style={{ margin: 0, flex: 2 }}>
          <label>Reason</label>
          <input required value={reason} onChange={(e) => setReason(e.target.value)}
                 style={{ width: "100%", border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
        </div>
        <button className="btn" type="submit">Raise flag</button>
      </form>

      <div className="card" style={{ overflow: "auto" }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Raised</th>
              <th>Target</th>
              <th>Reason</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {flags.map((f) => (
              <tr key={f.id}>
                <td className="mono" style={{ fontSize: 12 }}>{new Date(f.raisedAt).toLocaleString()}</td>
                <td className="mono" style={{ fontSize: 12 }}>{f.merchantId ? `merchant:${f.merchantId}` : `payment:${f.paymentId}`}</td>
                <td>{f.reason}</td>
                <td>
                  <span className={`badge ${f.status === "ACTIVE" ? "badge-bad" : "badge-good"}`}>{f.status}</span>
                </td>
                <td>
                  {f.status === "ACTIVE" && (
                    <button className="btn btn-secondary" onClick={() => clear(f.id)}>Clear</button>
                  )}
                </td>
              </tr>
            ))}
            {flags.length === 0 && (
              <tr><td colSpan={5} style={{ color: "var(--color-muted)" }}>No fraud flags raised.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
