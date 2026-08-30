"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits, parseToMinorUnits } from "@/lib/money";

const TIP_OPTIONS = [
  { method: "NONE", label: "No tip", pct: 0 },
  { method: "PERCENT_5", label: "5%", pct: 0.05 },
  { method: "PERCENT_10", label: "10%", pct: 0.1 },
  { method: "CUSTOM", label: "Custom", pct: null },
];

export default function TipPage() {
  const { qrToken } = useParams();
  const router = useRouter();
  const searchParams = useSearchParams();
  const contribution = Number(searchParams.get("contribution") || 0);

  const [bill, setBill] = useState(null);
  const [selected, setSelected] = useState("PERCENT_10");
  const [customTip, setCustomTip] = useState("");
  const [error, setError] = useState(null);

  useEffect(() => {
    apiFetch(`/api/v1/tables/scan/${qrToken}`)
      .then((scan) => setBill(scan.bill))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Couldn't load this bill."));
  }, [qrToken]);

  if (error) return <Screen><div className="error-banner">{error}</div></Screen>;
  if (!bill || !contribution) return <Screen />;

  const option = TIP_OPTIONS.find((o) => o.method === selected);
  let tipAmount = 0;
  if (option.pct !== null) {
    tipAmount = Math.round(contribution * option.pct);
  } else {
    tipAmount = parseToMinorUnits(customTip) || 0;
  }
  const total = contribution + tipAmount;

  function goToSelectBank() {
    router.push(`/tables/${qrToken}/select-bank?contribution=${contribution}&tip=${tipAmount}&tipMethod=${selected}`);
  }

  return (
    <Screen>
      <div style={{ fontSize: 12, color: "var(--color-muted)", fontWeight: 600, marginBottom: 4 }}>← Back</div>
      <div style={{ fontSize: 20, fontWeight: 700, marginBottom: 24 }}>Add a tip?</div>

      <div style={{ display: "flex", gap: 8, marginBottom: 24 }}>
        {TIP_OPTIONS.map((o) => (
          <button key={o.method} onClick={() => setSelected(o.method)}
                  className={selected === o.method ? "btn" : "btn btn-secondary"}
                  style={{ flex: 1, padding: "14px 8px" }}>
            {o.label}
          </button>
        ))}
      </div>

      {selected === "CUSTOM" && (
        <div className="field">
          <label>Custom tip (£)</label>
          <input placeholder="0.00" inputMode="decimal" value={customTip} onChange={(e) => setCustomTip(e.target.value)} />
        </div>
      )}

      <div className="card" style={{ padding: 16, marginTop: 8 }}>
        <Row label="Your share" value={formatMinorUnits(contribution, bill.currencyCode)} />
        <Row label={`Tip${option.pct ? ` (${Math.round(option.pct * 100)}%)` : ""}`} value={formatMinorUnits(tipAmount, bill.currencyCode)} />
        <div style={{ height: 1, background: "var(--color-line)", margin: "8px 0" }} />
        <div style={{ display: "flex", justifyContent: "space-between", fontWeight: 700, fontSize: 20 }}>
          <span>Total</span>
          <span className="mono">{formatMinorUnits(total, bill.currencyCode)}</span>
        </div>
      </div>

      <button className="btn" style={{ width: "100%", padding: "15px 16px", marginTop: 20 }} onClick={goToSelectBank}>
        Pay by Bank — {formatMinorUnits(total, bill.currencyCode)}
      </button>
    </Screen>
  );
}

function Row({ label, value }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 14, color: "var(--color-muted)", padding: "5px 0" }}>
      <span>{label}</span>
      <span className="mono">{value}</span>
    </div>
  );
}

function Screen({ children }) {
  return (
    <div style={{ minHeight: "100vh", padding: 24 }}>
      <div style={{ width: 340, margin: "40px auto 0" }}>{children}</div>
    </div>
  );
}
