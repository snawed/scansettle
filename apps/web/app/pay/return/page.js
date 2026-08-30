"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";
import { formatMinorUnits } from "@/lib/money";

const POLL_INTERVAL_MS = 1500;
const FAILURE_MESSAGES = {
  REJECTED: "Your bank declined this payment.",
  FAILED: "Something went wrong processing this payment.",
  CANCELLED: "This payment was cancelled.",
  EXPIRED: "This payment session expired.",
};

/**
 * Never trusts the browser having "returned" as proof of anything (docs/api.md) —
 * this page's only source of truth is polling the backend, which itself only
 * believes what the provider's webhook told it.
 *
 * useSearchParams() requires a Suspense boundary in a statically-analysable route
 * like this one (no dynamic segment) — see
 * https://nextjs.org/docs/messages/missing-suspense-with-csr-bailout.
 */
export default function ReturnPage() {
  return (
    <Suspense fallback={<Screen><LoadingMessage /></Screen>}>
      <ReturnPageContent />
    </Suspense>
  );
}

function ReturnPageContent() {
  const searchParams = useSearchParams();
  const paymentId = searchParams.get("paymentId");
  const [status, setStatus] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!paymentId) return;
    let cancelled = false;

    async function poll() {
      try {
        const result = await apiFetch(`/api/v1/payments/${paymentId}/status`);
        if (cancelled) return;
        setStatus(result);
        if (!result.terminal) {
          setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Couldn't check payment status.");
      }
    }

    poll();
    return () => {
      cancelled = true;
    };
  }, [paymentId]);

  if (error) {
    return (
      <Screen>
        <div className="error-banner">{error}</div>
      </Screen>
    );
  }

  if (!status || !status.terminal) {
    return (
      <Screen>
        <LoadingMessage />
      </Screen>
    );
  }

  if (status.state === "PAYMENT_CONFIRMED") {
    return (
      <Screen>
        <div style={{ width: 56, height: 56, borderRadius: 100, background: "var(--color-good-bg)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 20px" }}>
          <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
            <path d="M4 13L10 19L22 6" stroke="#12805C" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <div style={{ fontSize: 12, color: "var(--color-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 8 }}>
          Payment complete
        </div>
        <div className="mono" style={{ fontSize: 36, fontWeight: 600, marginBottom: 20 }}>
          {formatMinorUnits(status.amountMinorUnits, status.currencyCode)}
        </div>
        <div style={{ fontSize: 12, color: "var(--color-muted)" }}>You can close this window.</div>
      </Screen>
    );
  }

  return (
    <Screen>
      <div style={{ width: 56, height: 56, borderRadius: 100, background: "var(--color-bad-bg)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 20px" }}>
        <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
          <path d="M4 4L18 18M18 4L4 18" stroke="#C22A2A" strokeWidth="3" strokeLinecap="round" />
        </svg>
      </div>
      <div style={{ fontSize: 15, fontWeight: 700, marginBottom: 8 }}>Payment not completed</div>
      <div style={{ fontSize: 13, color: "var(--color-muted)" }}>
        {FAILURE_MESSAGES[status.state] || "This payment could not be completed."}
      </div>
    </Screen>
  );
}

function LoadingMessage() {
  return (
    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-muted)" }}>
      Confirming your payment…
    </div>
  );
}

function Screen({ children }) {
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: 24, textAlign: "center" }}>
      <div style={{ width: 320 }}>{children}</div>
    </div>
  );
}
