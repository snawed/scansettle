"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, ApiError, clearToken } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";
import { statusBadgeClass, statusLabel } from "@/lib/statusBadge";

const STATE_FILTERS = [
  { value: "", label: "All" },
  { value: "PAYMENT_CONFIRMED", label: "Confirmed" },
  { value: "REJECTED", label: "Rejected" },
  { value: "FAILED", label: "Failed" },
];

export default function PaymentsListPage() {
  const router = useRouter();
  const [payments, setPayments] = useState([]);
  const [stateFilter, setStateFilter] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const query = stateFilter ? `?state=${stateFilter}&size=50` : "?size=50";
        const result = await apiFetch(`/api/v1/payments${query}`);
        setPayments(result);
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          clearToken();
          router.replace("/login");
          return;
        }
        setError(err instanceof ApiError ? err.message : "Failed to load payments.");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [stateFilter, router]);

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <h1 style={{ fontSize: 20, fontWeight: 700 }}>Payments</h1>
        <div style={{ display: "flex", gap: 6 }}>
          {STATE_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => setStateFilter(f.value)}
              className={stateFilter === f.value ? "btn" : "btn btn-secondary"}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="card" style={{ overflow: "auto" }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Amount</th>
              <th>Reference</th>
              <th>Status</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {!loading && payments.length === 0 && (
              <tr>
                <td colSpan={4} style={{ color: "var(--color-muted)" }}>No payments found.</td>
              </tr>
            )}
            {payments.map((p) => (
              <tr key={p.id} onClick={() => router.push(`/dashboard/payments/${p.id}`)} style={{ cursor: "pointer" }}>
                <td className="mono" style={{ fontWeight: 600 }}>{formatMinorUnits(p.amountMinorUnits, p.currencyCode)}</td>
                <td className="mono">{p.id.slice(0, 8)}</td>
                <td><span className={statusBadgeClass(p.state)}>{statusLabel(p.state)}</span></td>
                <td>{new Date(p.createdAt).toLocaleString("en-GB")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
