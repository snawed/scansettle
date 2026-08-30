"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";

/**
 * ScanSettle's own stand-in for a real bank's login/consent screen — this is where
 * MockOpenBankingProvider's redirectUrl sends the customer (docs/payment-states.md's
 * REDIRECTED_TO_BANK). Approve/Decline here calls the backend's mock-bank decision
 * endpoint, which fires a genuine signed webhook — not a shortcut.
 */
export default function MockBankPage() {
  const { providerReference } = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();
  const paymentId = searchParams.get("paymentId");
  // Full relative path of the "return to ScanSettle" page for whichever journey
  // started this authorisation (trade payment vs. table bill) — generic so this
  // page doesn't need to know about every payment type that can redirect here.
  const returnTo = searchParams.get("returnTo") || "/pay/return";

  const [info, setInfo] = useState(null);
  const [error, setError] = useState(null);
  const [deciding, setDeciding] = useState(false);

  useEffect(() => {
    apiFetch(`/api/v1/mock-bank/${providerReference}`)
      .then(setInfo)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Couldn't load this authorisation request."));
  }, [providerReference]);

  async function decide(approve) {
    setDeciding(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/mock-bank/${providerReference}/decision?approve=${approve}`, { method: "POST" });
      router.push(`${returnTo}?paymentId=${paymentId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong — please try again.");
      setDeciding(false);
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "#0d1b3d", color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div style={{ width: 340, background: "#fff", color: "var(--color-ink)", borderRadius: 10, padding: 28 }}>
        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", color: "var(--color-muted)", textTransform: "uppercase", marginBottom: 4 }}>
          Your Bank (sandbox)
        </div>
        <div style={{ fontSize: 17, fontWeight: 700, marginBottom: 22 }}>Approve this payment?</div>

        {error && <div className="error-banner">{error}</div>}

        {info && (
          <div className="card" style={{ padding: 16, marginBottom: 22 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 8 }}>
              <span style={{ color: "var(--color-muted)" }}>Pay to</span>
              <span style={{ fontWeight: 600 }}>{info.merchantTradingName}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
              <span style={{ color: "var(--color-muted)" }}>Amount</span>
              <span className="mono" style={{ fontWeight: 700 }}>{formatMinorUnits(info.amountMinorUnits, info.currencyCode)}</span>
            </div>
          </div>
        )}

        <button className="btn" style={{ width: "100%", marginBottom: 8, padding: "12px 16px" }}
                disabled={!info || deciding} onClick={() => decide(true)}>
          {deciding ? "Processing…" : "Approve"}
        </button>
        <button className="btn btn-secondary" style={{ width: "100%", padding: "12px 16px" }}
                disabled={!info || deciding} onClick={() => decide(false)}>
          Decline
        </button>

        <div style={{ textAlign: "center", fontSize: 11, color: "var(--color-muted)", marginTop: 16 }}>
          This is a sandbox bank screen for local development — no real bank is involved.
        </div>
      </div>
    </div>
  );
}
