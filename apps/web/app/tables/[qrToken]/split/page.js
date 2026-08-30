"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits, parseToMinorUnits } from "@/lib/money";

export default function SplitBillPage() {
  const { qrToken } = useParams();
  const router = useRouter();
  const [bill, setBill] = useState(null);
  const [mode, setMode] = useState("split"); // "full" | "split" | "custom"
  const [splitCount, setSplitCount] = useState(2);
  const [customAmount, setCustomAmount] = useState("");
  const [error, setError] = useState(null);

  useEffect(() => {
    apiFetch(`/api/v1/tables/scan/${qrToken}`)
      .then((scan) => setBill(scan.bill))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Couldn't load this bill."));
  }, [qrToken]);

  if (error) return <Screen><div className="error-banner">{error}</div></Screen>;
  if (!bill) return <Screen />;

  const remaining = bill.remainingAmountMinorUnits;
  const splitShare = Math.round(remaining / splitCount);
  const customMinorUnits = parseToMinorUnits(customAmount);

  let contributionAmount = null;
  let continueLabel = "Continue";
  if (mode === "full") {
    contributionAmount = remaining;
    continueLabel = `Continue — ${formatMinorUnits(remaining, bill.currencyCode)}`;
  } else if (mode === "split") {
    contributionAmount = splitShare;
    continueLabel = `Continue — ${formatMinorUnits(splitShare, bill.currencyCode)}`;
  } else if (mode === "custom" && customMinorUnits) {
    contributionAmount = customMinorUnits;
    continueLabel = `Continue — ${formatMinorUnits(customMinorUnits, bill.currencyCode)}`;
  }

  function goToTip() {
    if (!contributionAmount || contributionAmount <= 0) return;
    router.push(`/tables/${qrToken}/tip?contribution=${contributionAmount}`);
  }

  return (
    <Screen>
      <div style={{ fontSize: 12, color: "var(--color-muted)", fontWeight: 600, marginBottom: 4 }}>
        ← Table {qrToken ? "" : ""}
      </div>
      <div style={{ fontSize: 20, fontWeight: 700, marginBottom: 4 }}>How much are you paying?</div>
      <div style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 24 }}>
        Remaining balance: {formatMinorUnits(remaining, bill.currencyCode)}
      </div>

      <Option selected={mode === "split"} onClick={() => setMode("split")}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15 }}>Split equally</div>
            <div style={{ fontSize: 12, color: "var(--color-muted)", marginTop: 2 }}>
              {mode === "split" && (
                <span style={{ display: "inline-flex", gap: 6, alignItems: "center", marginTop: 4 }}>
                  <button type="button" onClick={(e) => { e.stopPropagation(); setSplitCount((n) => Math.max(2, n - 1)); }} className="btn btn-secondary" style={{ padding: "2px 8px" }}>−</button>
                  {splitCount} people
                  <button type="button" onClick={(e) => { e.stopPropagation(); setSplitCount((n) => n + 1); }} className="btn btn-secondary" style={{ padding: "2px 8px" }}>+</button>
                </span>
              )}
            </div>
          </div>
          <div className="mono" style={{ fontWeight: 700 }}>{formatMinorUnits(splitShare, bill.currencyCode)}</div>
        </div>
      </Option>

      <Option selected={mode === "full"} onClick={() => setMode("full")}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div style={{ fontWeight: 700, fontSize: 15 }}>Pay full remaining</div>
          <div className="mono" style={{ fontWeight: 700 }}>{formatMinorUnits(remaining, bill.currencyCode)}</div>
        </div>
      </Option>

      <Option selected={mode === "custom"} onClick={() => setMode("custom")}>
        <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 10 }}>Custom amount</div>
        <input placeholder="0.00" inputMode="decimal" value={customAmount}
               onChange={(e) => { setMode("custom"); setCustomAmount(e.target.value); }}
               style={{ width: "100%", border: "1px solid var(--color-line)", borderRadius: 6, padding: "10px 12px", fontSize: 14, fontFamily: "inherit" }} />
      </Option>

      <button className="btn" style={{ width: "100%", padding: "14px 16px", marginTop: 8 }}
              disabled={!contributionAmount || contributionAmount <= 0} onClick={goToTip}>
        {continueLabel}
      </button>
    </Screen>
  );
}

function Option({ children, selected, onClick }) {
  return (
    <div onClick={onClick} className="card" style={{
      padding: 16, marginBottom: 10, cursor: "pointer",
      borderColor: selected ? "var(--color-accent)" : "var(--color-line)",
      borderWidth: selected ? 2 : 1, background: selected ? "#eef4ff" : "#fff",
    }}>
      {children}
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
