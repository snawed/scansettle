"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { getOpsToken, clearOpsToken } from "@/lib/opsApiClient";

const NAV_ITEMS = [
  { href: "/ops/merchants", label: "Merchants" },
  { href: "/ops/webhooks", label: "Webhooks" },
  { href: "/ops/investigate", label: "Investigate Payment" },
  { href: "/ops/fraud-flags", label: "Fraud Flags" },
  { href: "/ops/security", label: "Security" },
];

export default function OpsLayout({ children }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const isLoginPage = pathname === "/ops/login";

  useEffect(() => {
    if (isLoginPage) {
      setReady(true);
      return;
    }
    if (!getOpsToken()) {
      router.replace("/ops/login");
      return;
    }
    setReady(true);
  }, [router, isLoginPage]);

  function handleLogout() {
    clearOpsToken();
    router.replace("/ops/login");
  }

  if (!ready) {
    return null;
  }

  if (isLoginPage) {
    return children;
  }

  return (
    <div style={{ display: "flex", minHeight: "100vh" }}>
      <aside style={{ width: 220, flexShrink: 0, background: "var(--color-sidebar)", color: "var(--color-sidebar-ink)", padding: "20px 18px", display: "flex", flexDirection: "column" }}>
        <div style={{ color: "#fff", fontWeight: 700, fontSize: 16, marginBottom: 4 }}>ScanSettle</div>
        <div style={{ color: "var(--color-sidebar-ink)", fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", marginBottom: 24 }}>
          Ops
        </div>
        <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
          {NAV_ITEMS.map((item) => {
            const active = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                style={{
                  padding: "9px 12px",
                  borderRadius: 6,
                  fontWeight: 600,
                  fontSize: 13,
                  color: active ? "#fff" : "var(--color-sidebar-ink)",
                  background: active ? "var(--color-accent)" : "transparent",
                }}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div style={{ flex: 1 }} />
        <button onClick={handleLogout} className="btn-secondary btn" style={{ background: "transparent", color: "var(--color-sidebar-ink)", border: "1px solid #333a48" }}>
          Log out
        </button>
      </aside>

      <main style={{ flex: 1, padding: "28px 36px", overflow: "auto" }}>{children}</main>
    </div>
  );
}
