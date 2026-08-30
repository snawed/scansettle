"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { getToken, clearToken } from "@/lib/apiClient";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/dashboard/payments", label: "Payments" },
  { href: "/dashboard/payments/new", label: "Create Payment" },
  { href: "/dashboard/tables", label: "ScanSettle Tables" },
];

export default function DashboardLayout({ children }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    setReady(true);
  }, [router]);

  function handleLogout() {
    clearToken();
    router.replace("/login");
  }

  if (!ready) {
    return null;
  }

  return (
    <div style={{ display: "flex", minHeight: "100vh" }}>
      <aside style={{ width: 220, flexShrink: 0, background: "var(--color-sidebar)", color: "var(--color-sidebar-ink)", padding: "20px 18px", display: "flex", flexDirection: "column" }}>
        <div style={{ color: "#fff", fontWeight: 700, fontSize: 16, marginBottom: 28 }}>ScanSettle</div>
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
