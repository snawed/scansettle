"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetch, setToken, ApiError } from "@/lib/apiClient";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    legalName: "",
    tradingName: "",
    businessType: "",
    email: "",
    password: "",
  });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await apiFetch("/api/v1/merchants", { method: "POST", body: JSON.stringify(form) });
      const login = await apiFetch("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: form.email, password: form.password }),
      });
      setToken(login.accessToken);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div style={{ width: 400 }}>
        <div style={{ fontWeight: 700, fontSize: 18, marginBottom: 4 }}>ScanSettle</div>
        <div style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 28 }}>
          Pay by Bank, made simple. Create your merchant account.
        </div>

        {error && <div className="error-banner">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="tradingName">Trading name</label>
            <input id="tradingName" required value={form.tradingName} onChange={update("tradingName")}
                   placeholder="Dave's Heating &amp; Plumbing" />
          </div>
          <div className="field">
            <label htmlFor="legalName">Legal business name</label>
            <input id="legalName" required value={form.legalName} onChange={update("legalName")}
                   placeholder="Dave's Heating Ltd" />
          </div>
          <div className="field">
            <label htmlFor="businessType">Business type</label>
            <input id="businessType" required value={form.businessType} onChange={update("businessType")}
                   placeholder="Plumbing" />
          </div>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" required value={form.email} onChange={update("email")} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" required minLength={10} value={form.password}
                   onChange={update("password")} />
          </div>

          <button type="submit" className="btn" style={{ width: "100%", padding: "11px 16px" }} disabled={submitting}>
            {submitting ? "Creating account…" : "Create account"}
          </button>
        </form>

        <div style={{ fontSize: 13, color: "var(--color-muted)", marginTop: 16, textAlign: "center" }}>
          Already have an account? <Link href="/login">Log in</Link>
        </div>
      </div>
    </div>
  );
}
