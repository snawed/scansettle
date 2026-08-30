"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function OpsIndexPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/ops/merchants");
  }, [router]);
  return null;
}
