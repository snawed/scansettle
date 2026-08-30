"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";

export default function TableBillPage() {
  const { qrToken } = useParams();
  const router = useRouter();
  const [scan, setScan] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    apiFetch(`/api/v1/tables/scan/${qrToken}`)
      .then(setScan)
      .catch((err) => setError(err instanceof ApiError ? err.message : "This table couldn't be found."));
  }, [qrToken]);

  if (error) {
    return (
      <CenteredScreen>
        <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 6 }}>Bill unavailable</div>
        <div style={{ fontSize: 13, color: "var(--color-muted)" }}>{error}</div>
      </CenteredScreen>
    );
  }

  if (!scan) {
    return <CenteredScreen />;
  }

  if (scan.occupancyStatus === "FREE" || !scan.bill) {
    return (
      <CenteredScreen>
        <div style={{ fontSize: 11, color: "var(--color-accent)", fontWeight: 800, letterSpacing: "0.06em", textTransform: "uppercase", marginBottom: 4 }}>
          ScanSettle Tables
        </div>
        <div style={{ fontSize: 22, fontWeight: 700, marginBottom: 2 }}>{scan.venueName}</div>
        <div style={{ fontSize: 14, color: "var(--color-muted)", fontWeight: 600, marginBottom: 24 }}>{scan.tableLabel}</div>
        <div className="card" style={{ padding: 16, textAlign: "center" }}>
          <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 6 }}>No bill open yet</div>
          <div style={{ fontSize: 13, color: "var(--color-muted)" }}>
            This table doesn&apos;t have an open bill — please ask a member of staff.
          </div>
        </div>
      </CenteredScreen>
    );
  }

  const { bill } = scan;
  const isPaid = bill.state === "PAID";

  return (
    <CenteredScreen>
      <div style={{ fontSize: 11, color: "var(--color-accent)", fontWeight: 800, letterSpacing: "0.06em", textTransform: "uppercase", marginBottom: 4 }}>
        ScanSettle Tables
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, marginBottom: 2 }}>{scan.venueName}</div>
      <div style={{ fontSize: 14, color: "var(--color-muted)", fontWeight: 600, marginBottom: 24 }}>{scan.tableLabel}</div>

      <div style={{ marginBottom: 4 }}>
        {bill.lineItems.map((item, i) => (
          <Row key={i} label={item.description} value={formatMinorUnits(item.amountMinorUnits, bill.currencyCode)} muted />
        ))}
        <Row label="Total" value={formatMinorUnits(bill.totalAmountMinorUnits, bill.currencyCode)} bold divider />
      </div>

      <div style={{ height: 1, background: "var(--color-line)", margin: "20px 0" }} />

      <Row label="Already paid" value={formatMinorUnits(bill.paidAmountMinorUnits, bill.currencyCode)} muted />
      <div style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", fontWeight: 700, fontSize: 24 }}>
        <span>Remaining</span>
        <span className="mono">{formatMinorUnits(bill.remainingAmountMinorUnits, bill.currencyCode)}</span>
      </div>

      <div style={{ marginTop: 28 }}>
        {isPaid ? (
          <div className="badge badge-good" style={{ display: "block", textAlign: "center", padding: "12px 16px", fontSize: 13 }}>
            This bill is fully paid
          </div>
        ) : (
          <button className="btn" style={{ width: "100%", padding: "15px 16px", fontSize: 15 }}
                  onClick={() => router.push(`/tables/${qrToken}/split`)}>
            Pay by Bank
          </button>
        )}
      </div>
    </CenteredScreen>
  );
}

function Row({ label, value, bold, muted, divider }) {
  return (
    <div style={{
      display: "flex", justifyContent: "space-between", padding: "8px 0", fontSize: 14,
      fontWeight: bold ? 700 : 400, color: muted ? "var(--color-muted)" : "var(--color-ink)",
      borderTop: divider ? "1px solid var(--color-line)" : "none", marginTop: divider ? 6 : 0,
      paddingTop: divider ? 12 : 8,
    }}>
      <span>{label}</span>
      <span className="mono">{value}</span>
    </div>
  );
}

function CenteredScreen({ children }) {
  return (
    <div style={{ minHeight: "100vh", padding: 24 }}>
      <div style={{ width: 340, margin: "40px auto 0" }}>{children}</div>
    </div>
  );
}
