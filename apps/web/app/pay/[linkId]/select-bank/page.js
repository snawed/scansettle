"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/apiClient";

export default function SelectBankPage() {
  const { linkId } = useParams();
  const router = useRouter();
  const [banks, setBanks] = useState([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState(null);
  const [choosing, setChoosing] = useState(null);

  useEffect(() => {
    apiFetch("/api/v1/open-banking/banks")
      .then(setBanks)
      .catch(() => setError("Couldn't load banks — please try again."));
  }, []);

  async function chooseBank(bank) {
    setError(null);
    setChoosing(bank.id);
    try {
      const result = await apiFetch(`/api/v1/payment-links/${linkId}/payments?bankId=${bank.id}`, { method: "POST" });
      const destination = new URL(result.redirectUrl);
      destination.searchParams.set("paymentId", result.paymentId);
      destination.searchParams.set("returnTo", "/pay/return");
      window.location.href = destination.toString();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't start the payment — please try again.");
      setChoosing(null);
    }
  }

  const filtered = banks.filter((b) => b.name.toLowerCase().includes(query.toLowerCase()));

  return (
    <div style={{ minHeight: "100vh", padding: "28px 24px", maxWidth: 400, margin: "0 auto" }}>
      <div onClick={() => router.back()} style={{ fontSize: 12, color: "var(--color-muted)", fontWeight: 600, marginBottom: 4, cursor: "pointer" }}>
        ← Cancel
      </div>
      <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 14 }}>Choose your bank</div>

      <input placeholder="Search banks" value={query} onChange={(e) => setQuery(e.target.value)}
             style={{ width: "100%", border: "1px solid var(--color-line)", borderRadius: 6, padding: "10px 12px", fontSize: 13, marginBottom: 6, fontFamily: "inherit" }} />

      {error && <div className="error-banner">{error}</div>}

      <div style={{ marginTop: 6 }}>
        {filtered.map((bank) => (
          <div key={bank.id} onClick={() => !choosing && chooseBank(bank)}
               style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 8px", borderBottom: "1px solid #eef0f2", cursor: "pointer", opacity: choosing && choosing !== bank.id ? 0.5 : 1 }}>
            <div style={{ width: 32, height: 32, borderRadius: 6, background: "var(--color-bg)", border: "1px solid var(--color-line)", flexShrink: 0 }} />
            <div style={{ fontWeight: 600, fontSize: 14, flex: 1 }}>{bank.name}</div>
            {choosing === bank.id && <div style={{ fontSize: 12, color: "var(--color-muted)" }}>Connecting…</div>}
          </div>
        ))}
      </div>
    </div>
  );
}
