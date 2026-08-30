"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetch, ApiError, clearToken } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";
import { statusBadgeClass, statusLabel } from "@/lib/statusBadge";

export default function DashboardPage() {
  const router = useRouter();
  const [summary, setSummary] = useState(null);
  const [payments, setPayments] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function load() {
      try {
        const [summaryResult, paymentsResult] = await Promise.all([
          apiFetch("/api/v1/dashboard/summary"),
          apiFetch("/api/v1/payments?size=5"),
        ]);
        setSummary(summaryResult);
        setPayments(paymentsResult);
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          clearToken();
          router.replace("/login");
          return;
        }
        setError(err instanceof ApiError ? err.message : "Failed to load dashboard.");
      }
    }
    load();
  }, [router]);

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>Dashboard</h1>

      {error && <div className="error-banner">{error}</div>}

      {summary && (
        <div style={{ display: "flex", gap: 12, marginBottom: 28, flexWrap: "wrap" }}>
          <StatCard label="Today" value={formatMinorUnits(summary.todayConfirmedAmountMinorUnits)} sub={`${summary.todayConfirmedCount} payment${summary.todayConfirmedCount === 1 ? "" : "s"}`} />
          <StatCard label="This month" value={formatMinorUnits(summary.monthConfirmedAmountMinorUnits)} />
          <StatCard label="Fees this month" value={formatMinorUnits(summary.monthFeesMinorUnits)} />
          <StatCard label="Pending" value={String(summary.pendingCount)} />
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <div style={{ fontSize: 14, fontWeight: 700 }}>Recent payments</div>
        <Link href="/dashboard/payments" style={{ fontSize: 13, fontWeight: 600 }}>
          View all
        </Link>
      </div>

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
            {payments.length === 0 && (
              <tr>
                <td colSpan={4} style={{ color: "var(--color-muted)" }}>
                  No payments yet — <Link href="/dashboard/payments/new">create your first payment</Link>.
                </td>
              </tr>
            )}
            {payments.map((p) => (
              <tr key={p.id} onClick={() => router.push(`/dashboard/payments/${p.id}`)} style={{ cursor: "pointer" }}>
                <td className="mono" style={{ fontWeight: 600 }}>{formatMinorUnits(p.amountMinorUnits, p.currencyCode)}</td>
                <td className="mono">{p.id.slice(0, 8)}</td>
                <td>
                  <span className={statusBadgeClass(p.state)}>{statusLabel(p.state)}</span>
                </td>
                <td>{new Date(p.createdAt).toLocaleString("en-GB")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatCard({ label, value, sub }) {
  return (
    <div className="card" style={{ padding: "14px 16px", minWidth: 160 }}>
      <div style={{ fontSize: 11, color: "var(--color-muted)", fontWeight: 700, textTransform: "uppercase" }}>{label}</div>
      <div className="mono" style={{ fontSize: 20, fontWeight: 600, marginTop: 4 }}>{value}</div>
      {sub && <div style={{ fontSize: 12, color: "var(--color-muted)", marginTop: 2 }}>{sub}</div>}
    </div>
  );
}
