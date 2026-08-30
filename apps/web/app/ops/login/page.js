"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { opsFetch, setOpsToken } from "@/lib/opsApiClient";
import { ApiError } from "@/lib/apiClient";

export default function OpsLoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mfaChallengeToken, setMfaChallengeToken] = useState(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleLogin(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await opsFetch("/api/v1/admin/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      if (result.accessToken) {
        setOpsToken(result.accessToken);
        router.push("/ops/merchants");
      } else {
        setMfaChallengeToken(result.mfaChallengeToken);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleMfaVerify(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await opsFetch("/api/v1/admin/auth/mfa/verify-login", {
        method: "POST",
        body: JSON.stringify({ mfaChallengeToken, code }),
      });
      setOpsToken(result.accessToken);
      router.push("/ops/merchants");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Incorrect code — please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div style={{ width: 360 }}>
        <div style={{ fontWeight: 700, fontSize: 18, marginBottom: 4 }}>ScanSettle Ops</div>
        <div style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 28 }}>
          {mfaChallengeToken ? "Enter your authenticator code" : "Internal — ScanSettle support/ops staff only"}
        </div>

        {error && <div className="error-banner">{error}</div>}

        {!mfaChallengeToken ? (
          <form onSubmit={handleLogin}>
            <div className="field">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input id="password" type="password" required value={password}
                     onChange={(e) => setPassword(e.target.value)} />
            </div>
            <button type="submit" className="btn" style={{ width: "100%", padding: "11px 16px" }} disabled={submitting}>
              {submitting ? "Logging in…" : "Log in"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleMfaVerify}>
            <div className="field">
              <label htmlFor="code">6-digit code</label>
              <input id="code" required autoFocus inputMode="numeric" maxLength={6} value={code}
                     onChange={(e) => setCode(e.target.value)} />
            </div>
            <button type="submit" className="btn" style={{ width: "100%", padding: "11px 16px" }} disabled={submitting}>
              {submitting ? "Verifying…" : "Verify"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
