"use client";

import { useEffect, useState } from "react";
import { opsFetch } from "@/lib/opsApiClient";
import { ApiError } from "@/lib/apiClient";

export default function OpsMerchantsPage() {
  const [merchants, setMerchants] = useState([]);
  const [q, setQ] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    load();
  }, []);

  async function load(query) {
    setLoading(true);
    try {
      const path = query ? `/api/v1/admin/merchants?q=${encodeURIComponent(query)}` : "/api/v1/admin/merchants";
      setMerchants(await opsFetch(path));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load merchants.");
    } finally {
      setLoading(false);
    }
  }

  function handleSearch(e) {
    e.preventDefault();
    load(q);
  }

  async function toggleStatus(merchant) {
    try {
      const action = merchant.status === "SUSPENDED" ? "reactivate" : "suspend";
      await opsFetch(`/api/v1/admin/merchants/${merchant.id}/${action}`, { method: "POST" });
      load(q);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to update merchant status.");
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>Merchants</h1>
      {error && <div className="error-banner">{error}</div>}

      <form onSubmit={handleSearch} style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        <input placeholder="Search by trading name" value={q} onChange={(e) => setQ(e.target.value)}
               style={{ width: 320, border: "1px solid var(--color-line)", borderRadius: 6, padding: "8px 10px", fontSize: 13 }} />
        <button className="btn btn-secondary" type="submit">Search</button>
      </form>

      <div className="card" style={{ overflow: "auto" }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Trading name</th>
              <th>Legal name</th>
              <th>Business type</th>
              <th>Verification</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {merchants.map((m) => (
              <tr key={m.id}>
                <td style={{ fontWeight: 600 }}>{m.tradingName}</td>
                <td>{m.legalName}</td>
                <td>{m.businessType}</td>
                <td>{m.verificationStatus}</td>
                <td>
                  <span className={`badge ${m.status === "SUSPENDED" ? "badge-bad" : "badge-good"}`}>
                    {m.status}
                  </span>
                </td>
                <td>
                  <button className="btn btn-secondary" onClick={() => toggleStatus(m)}>
                    {m.status === "SUSPENDED" ? "Reactivate" : "Suspend"}
                  </button>
                </td>
              </tr>
            ))}
            {!loading && merchants.length === 0 && (
              <tr><td colSpan={6} style={{ color: "var(--color-muted)" }}>No merchants found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
