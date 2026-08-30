"use client";

import { ApiError } from "@/lib/apiClient";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const TOKEN_KEY = "scansettle_ops_access_token";

export function getOpsToken() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setOpsToken(token) {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function clearOpsToken() {
  window.localStorage.removeItem(TOKEN_KEY);
}

/** Same shape as apiClient's apiFetch, but reads the separate ops token so an ops
 *  session and a merchant session in the same browser never collide. */
export async function opsFetch(path, options = {}) {
  const token = getOpsToken();
  const headers = { ...(options.headers || {}) };
  if (options.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

  if (!res.ok) {
    let problem = null;
    try {
      problem = await res.json();
    } catch {
      // non-JSON error body — fall through with a generic message
    }
    throw new ApiError(problem, res.status);
  }

  if (res.status === 204) return null;
  const contentType = res.headers.get("Content-Type") || "";
  return contentType.includes("json") ? res.json() : res.text();
}
