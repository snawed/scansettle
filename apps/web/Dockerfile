# syntax=docker/dockerfile:1

FROM node:26-alpine AS deps
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci

FROM node:26-alpine AS build
WORKDIR /build
COPY --from=deps /build/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* vars are inlined into the client bundle at build time, not read at
# container start — so this has to be a build arg, not a runtime env var. It's the
# browser's address for the API (typically the host-exposed port), which differs
# from the server-side API_BASE_URL used by Server Components (the Docker-internal
# service name) — see infra/docker-compose.yml.
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN npm run build

FROM node:26-alpine AS runner
RUN addgroup -S scansettle && adduser -S scansettle -G scansettle
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /build/public ./public
COPY --from=build --chown=scansettle:scansettle /build/.next/standalone ./
COPY --from=build --chown=scansettle:scansettle /build/.next/static ./.next/static
USER scansettle

EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
