"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetch, ApiError, clearToken } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";
import { statusBadgeClass, statusLabel } from "@/lib/statusBadge";

export default function PaymentDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function load() {
      try {
        const result = await apiFetch(`/api/v1/payments/${id}`);
        setPayment(result);
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          clearToken();
          router.replace("/login");
          return;
        }
        setError(err instanceof ApiError ? err.message : "Payment not found.");
      }
    }
    load();
  }, [id, router]);

  return (
    <div style={{ maxWidth: 480 }}>
      <Link href="/dashboard/payments" style={{ fontSize: 12, color: "var(--color-muted)", fontWeight: 600 }}>
        ← Back to payments
      </Link>

      <h1 style={{ fontSize: 20, fontWeight: 700, marginTop: 12, marginBottom: 20 }}>Payment detail</h1>

      {error && <div className="error-banner">{error}</div>}

      {payment && (
        <div className="card">
          <Row label="Amount" value={<span className="mono" style={{ fontWeight: 700, fontSize: 16 }}>{formatMinorUnits(payment.amountMinorUnits, payment.currencyCode)}</span>} />
          <Row label="Status" value={<span className={statusBadgeClass(payment.state)}>{statusLabel(payment.state)}</span>} />
          <Row label="Reference" value={<span className="mono">{payment.id}</span>} />
          <Row label="Created" value={new Date(payment.createdAt).toLocaleString("en-GB")} />
          <Row label="Last updated" value={new Date(payment.updatedAt).toLocaleString("en-GB")} />
        </div>
      )}
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderBottom: "1px solid var(--color-line)", fontSize: 13 }}>
      <span style={{ color: "var(--color-muted)" }}>{label}</span>
      <span>{value}</span>
    </div>
  );
}
