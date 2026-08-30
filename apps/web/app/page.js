import Link from "next/link";

export default function Home() {
  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "20px 32px" }}>
        <div style={{ fontWeight: 700, fontSize: 16 }}>ScanSettle</div>
        <nav style={{ display: "flex", gap: 20, alignItems: "center" }}>
          <Link href="/login" style={{ fontSize: 13, color: "var(--color-ink)", fontWeight: 600 }}>
            Log in
          </Link>
          <Link href="/register" className="btn">
            Create account
          </Link>
        </nav>
      </header>

      <main style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: 24, textAlign: "center" }}>
        <div style={{ fontSize: 40, fontWeight: 700, letterSpacing: "-0.02em", maxWidth: 560, lineHeight: 1.15 }}>
          Pay by Bank, made simple.
        </div>
        <p style={{ fontSize: 15, color: "var(--color-muted)", maxWidth: 480, marginTop: 16 }}>
          Get paid directly into your bank account — no card fees, no card terminal.
          Send a link, generate a QR code, done.
        </p>
        <Link href="/register" className="btn" style={{ marginTop: 28, padding: "12px 24px", fontSize: 14 }}>
          Get started
        </Link>
      </main>
    </div>
  );
}
