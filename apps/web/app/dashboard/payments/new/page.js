"use client";

import { useEffect, useState } from "react";
import { apiFetch, ApiError, getToken } from "@/lib/apiClient";
import { parseToMinorUnits, formatMinorUnits } from "@/lib/money";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export default function CreatePaymentPage() {
  const [form, setForm] = useState({ amount: "", description: "", reference: "" });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [createdLink, setCreatedLink] = useState(null);

  function update(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);

    const amountMinorUnits = parseToMinorUnits(form.amount);
    if (amountMinorUnits === null || amountMinorUnits <= 0) {
      setError("Enter a valid amount, e.g. 2500.00");
      return;
    }

    setSubmitting(true);
    try {
      const link = await apiFetch("/api/v1/payment-links", {
        method: "POST",
        body: JSON.stringify({
          amountMinorUnits,
          currencyCode: "GBP",
          description: form.description,
          reference: form.reference,
        }),
      });
      setCreatedLink(link);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create payment link.");
    } finally {
      setSubmitting(false);
    }
  }

  if (createdLink) {
    return <CreatedLinkView link={createdLink} onCreateAnother={() => setCreatedLink(null)} />;
  }

  return (
    <div style={{ maxWidth: 440 }}>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 4 }}>Create a payment</h1>
      <p style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 24 }}>
        Get paid directly into your bank account — no card fees.
      </p>

      {error && <div className="error-banner">{error}</div>}

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="amount">Amount (£)</label>
          <input id="amount" required inputMode="decimal" placeholder="2500.00" value={form.amount}
                 onChange={update("amount")} />
        </div>
        <div className="field">
          <label htmlFor="description">Description</label>
          <input id="description" required placeholder="Boiler Installation" value={form.description}
                 onChange={update("description")} />
        </div>
        <div className="field">
          <label htmlFor="reference">Invoice reference</label>
          <input id="reference" required placeholder="INV-1023" value={form.reference}
                 onChange={update("reference")} />
        </div>

        <button type="submit" className="btn" style={{ width: "100%", padding: "11px 16px" }} disabled={submitting}>
          {submitting ? "Creating…" : "Generate ScanSettle Link & QR"}
        </button>
      </form>
    </div>
  );
}

function CreatedLinkView({ link, onCreateAnother }) {
  const [copied, setCopied] = useState(false);
  const [qrObjectUrl, setQrObjectUrl] = useState(null);

  useEffect(() => {
    let objectUrl = null;
    let cancelled = false;

    // A plain <img src> can't carry the Authorization header the QR endpoint
    // requires, so fetch it as a blob and hand the browser an object URL instead.
    fetch(`${API_BASE_URL}/api/v1/payment-links/${link.id}/qr`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    })
      .then((res) => res.blob())
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setQrObjectUrl(objectUrl);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [link.id]);

  function copyLink() {
    navigator.clipboard.writeText(link.url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <div style={{ maxWidth: 440 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 20 }}>
        <span className="badge badge-good">Created</span>
      </div>

      <div className="card" style={{ padding: 24, textAlign: "center", marginBottom: 20 }}>
        {qrObjectUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={qrObjectUrl}
            alt="QR code for this payment link"
            width={200}
            height={200}
            style={{ margin: "0 auto 16px", display: "block" }}
          />
        ) : (
          <div style={{ width: 200, height: 200, margin: "0 auto 16px", background: "var(--color-bg)", borderRadius: "var(--radius)" }} />
        )}
        <div className="mono" style={{ fontSize: 22, fontWeight: 600 }}>
          {formatMinorUnits(link.amountMinorUnits, link.currencyCode)}
        </div>
        <div style={{ fontSize: 13, color: "var(--color-muted)", marginTop: 4 }}>{link.description}</div>
      </div>

      <div className="field">
        <label>Link</label>
        <div style={{ display: "flex", gap: 8 }}>
          <input readOnly value={link.url} style={{ flex: 1 }} />
          <button className="btn btn-secondary" onClick={copyLink} type="button">
            {copied ? "Copied" : "Copy"}
          </button>
        </div>
      </div>

      <button className="btn btn-secondary" style={{ width: "100%", marginTop: 8 }} onClick={onCreateAnother}>
        Create another payment
      </button>
    </div>
  );
}
