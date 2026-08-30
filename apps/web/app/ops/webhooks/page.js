"use client";

import { useEffect, useState } from "react";
import { opsFetch } from "@/lib/opsApiClient";
import { ApiError } from "@/lib/apiClient";

export default function OpsWebhooksPage() {
  const [webhooks, setWebhooks] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    opsFetch("/api/v1/admin/webhooks")
      .then(setWebhooks)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load webhooks."));
  }, []);

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>Webhooks</h1>
      {error && <div className="error-banner">{error}</div>}

      <div className="card" style={{ overflow: "auto" }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Received</th>
              <th>Source</th>
              <th>Provider reference</th>
              <th>Signature</th>
              <th>Result</th>
            </tr>
          </thead>
          <tbody>
            {webhooks.map((w) => (
              <tr key={w.id}>
                <td className="mono" style={{ fontSize: 12 }}>{new Date(w.receivedAt).toLocaleString()}</td>
                <td>{w.source}</td>
                <td className="mono" style={{ fontSize: 12 }}>{w.providerReference || "—"}</td>
                <td>
                  <span className={`badge ${w.signatureValid ? "badge-good" : "badge-bad"}`}>
                    {w.signatureValid ? "Valid" : "Invalid"}
                  </span>
                </td>
                <td>{w.processingResult || "—"}</td>
              </tr>
            ))}
            {webhooks.length === 0 && (
              <tr><td colSpan={5} style={{ color: "var(--color-muted)" }}>No webhooks received yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
