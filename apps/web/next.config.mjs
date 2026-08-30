import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  // Pin the workspace root explicitly — otherwise Turbopack's root detection can
  // pick up an unrelated lockfile above the repo (e.g. in the user's home dir).
  turbopack: {
    root: __dirname,
  },
  // A self-contained server bundle for the Docker runtime image (infra/docker-compose.yml).
  output: "standalone",
};

export default nextConfig;
