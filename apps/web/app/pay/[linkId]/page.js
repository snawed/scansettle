"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";

export default function PayByBankPage() {
  const { linkId } = useParams();
  const router = useRouter();
  const [link, setLink] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    apiFetch(`/api/v1/payment-links/${linkId}/public`)
      .then(setLink)
      .catch((err) => setError(err instanceof ApiError ? err.message : "This payment link couldn't be found."));
  }, [linkId]);

  if (error) {
    return (
      <CenteredScreen>
        <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 6 }}>Link unavailable</div>
        <div style={{ fontSize: 13, color: "var(--color-muted)" }}>{error}</div>
      </CenteredScreen>
    );
  }

  if (!link) {
    return <CenteredScreen />;
  }

  return (
    <CenteredScreen>
      <div style={{ width: 34, height: 34, borderRadius: 6, background: "var(--color-bg)", border: "1px solid var(--color-line)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 12, marginBottom: 18 }}>
        {link.merchantTradingName.slice(0, 2).toUpperCase()}
      </div>
      <div style={{ fontSize: 14, color: "var(--color-muted)", fontWeight: 600, marginBottom: 6 }}>
        {link.merchantTradingName}
      </div>
      <div className="mono" style={{ fontSize: 44, fontWeight: 600, letterSpacing: "-0.01em", marginBottom: 10 }}>
        {formatMinorUnits(link.amountMinorUnits, link.currencyCode)}
      </div>
      <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 2 }}>{link.description}</div>
      <div className="mono" style={{ fontSize: 12, color: "var(--color-muted)", marginBottom: 32 }}>REF: {link.reference}</div>

      {link.payable ? (
        <>
          <button className="btn" style={{ width: "100%", padding: "15px 16px", fontSize: 15 }}
                  onClick={() => router.push(`/pay/${linkId}/select-bank`)}>
            Pay by Bank
          </button>
          <div style={{ textAlign: "center", fontSize: 11, color: "var(--color-muted)", marginTop: 14, lineHeight: 1.5 }}>
            Secured by your bank&apos;s login. No card details, no ScanSettle account needed.
          </div>
        </>
      ) : (
        <div className="error-banner" style={{ textAlign: "center" }}>This payment link is no longer active.</div>
      )}
    </CenteredScreen>
  );
}

function CenteredScreen({ children }) {
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div style={{ width: 340, textAlign: "left" }}>{children}</div>
    </div>
  );
}
