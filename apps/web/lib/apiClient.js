"use client";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const TOKEN_KEY = "scansettle_access_token";

export function getToken() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  window.localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(problem, status) {
    super(problem?.detail || `Request failed with status ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

/** Thin fetch wrapper: attaches the bearer token, parses RFC 7807 error bodies. */
export async function apiFetch(path, options = {}) {
  const token = getToken();
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
