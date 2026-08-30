"use client";

import { useState } from "react";
import { opsFetch } from "@/lib/opsApiClient";
import { ApiError } from "@/lib/apiClient";

export default function OpsSecurityPage() {
  const [enrollment, setEnrollment] = useState(null);
  const [code, setCode] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState(null);

  async function startEnrollment() {
    setError(null);
    try {
      setEnrollment(await opsFetch("/api/v1/admin/auth/mfa/enroll", { method: "POST" }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to start MFA enrollment.");
    }
  }

  async function confirmEnrollment(e) {
    e.preventDefault();
    setError(null);
    try {
      await opsFetch("/api/v1/admin/auth/mfa/verify", { method: "POST", body: JSON.stringify({ code }) });
      setConfirmed(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Incorrect code — please try again.");
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20 }}>Security</h1>
      {error && <div className="error-banner">{error}</div>}

      <div className="card" style={{ padding: 16, maxWidth: 480 }}>
        <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 6 }}>Two-factor authentication</div>

        {confirmed ? (
          <div className="badge badge-good" style={{ display: "inline-block" }}>MFA enabled on this account</div>
        ) : !enrollment ? (
          <>
            <div style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 12 }}>
              Require a 6-digit authenticator code on top of your password for this ops login.
            </div>
            <button className="btn" onClick={startEnrollment}>Set up two-factor authentication</button>
          </>
        ) : (
          <form onSubmit={confirmEnrollment}>
            <div style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 8 }}>
              Add this to your authenticator app, then enter the current code to confirm.
            </div>
            <div style={{ marginBottom: 4 }}>
              <span style={{ fontSize: 12, color: "var(--color-muted)" }}>Manual entry key</span>
              <div className="mono" style={{ fontSize: 13, background: "var(--color-bg)", padding: "6px 8px", borderRadius: 6, marginTop: 2, wordBreak: "break-all" }}>
                {enrollment.secret}
              </div>
            </div>
            <div style={{ marginBottom: 12 }}>
              <span style={{ fontSize: 12, color: "var(--color-muted)" }}>Or import this URI</span>
              <div className="mono" style={{ fontSize: 11, background: "var(--color-bg)", padding: "6px 8px", borderRadius: 6, marginTop: 2, wordBreak: "break-all" }}>
                {enrollment.otpAuthUri}
              </div>
            </div>
            <div className="field">
              <label htmlFor="code">6-digit code</label>
              <input id="code" required autoFocus inputMode="numeric" maxLength={6} value={code}
                     onChange={(e) => setCode(e.target.value)} />
            </div>
            <button className="btn" type="submit">Confirm</button>
          </form>
        )}
      </div>
    </div>
  );
}
