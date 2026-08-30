# ScanSettle

**Pay by Bank, made simple.**

UK Account-to-Account payments platform (Open Banking). See [`/docs`](docs/) for the
full product and architecture design (start with
[`docs/architecture.md`](docs/architecture.md)).

## Status

Phase 2 — Technical Foundation. Merchant/payment domain logic has not been built
yet (Phase 3 onward); this is the runnable skeleton: auth/RBAC foundation, error
handling, correlation IDs, the `OpenBankingProvider` abstraction with a working
`MockOpenBankingProvider`, health checks, OpenAPI docs, and CI.

## Repository layout

```
apps/api    Spring Boot modular monolith (Java 21)
apps/web    Next.js frontend (JavaScript, App Router)
infra       docker-compose.yml, Terraform (later phases)
docs        Product & architecture design, ADRs
```

## Running locally

### Everything, via Docker Compose

```bash
cp infra/.env.example infra/.env   # adjust if needed — defaults are dev-only
cd infra
docker compose up -d --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8080 (Swagger UI: http://localhost:8080/swagger-ui.html)
- Postgres: localhost:5432 (`scansettle` / `scansettle`)

Stop with `docker compose down` (add `-v` to also drop the Postgres volume).

### Backend only, for development

Requires a local Postgres (or `docker compose up -d postgres` from `infra/`).

```bash
cd apps/api
APP_JWT_SECRET="dev-only-jwt-signing-secret-do-not-use-in-prod-32bytes+" \
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend only, for development

```bash
cd apps/web
npm install
API_BASE_URL=http://localhost:8080 npm run dev
```

## Testing

```bash
cd apps/api
mvn test      # fast unit tests
mvn verify    # + Testcontainers-backed integration tests (needs Docker running)
```

```bash
cd apps/web
npm run lint
npm run build
```

## Trying the auth/RBAC foundation

`dev`/`test` profiles only — replaced by real merchant login in Phase 3:

```bash
curl -X POST localhost:8080/api/v1/dev/token \
  -H "Content-Type: application/json" \
  -d '{"role":"ADMIN","merchantId":"demo-merchant"}'
# -> { "accessToken": "..." }

curl localhost:8080/api/v1/dev/whoami -H "Authorization: Bearer <token>"
curl localhost:8080/api/v1/dev/admin-only -H "Authorization: Bearer <token>"
```

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — system design, modules, tech decisions
- [`docs/domain-model.md`](docs/domain-model.md) — entities, ERD
- [`docs/payment-states.md`](docs/payment-states.md) — payment/bill state machines
- [`docs/scansettle-tables.md`](docs/scansettle-tables.md) — concurrency design for split bills
- [`docs/security.md`](docs/security.md) — security model
- [`docs/api.md`](docs/api.md) — API catalogue
- [`docs/open-banking.md`](docs/open-banking.md), [`docs/pos-integration.md`](docs/pos-integration.md) — provider abstractions
- [`docs/decisions/`](docs/decisions/) — ADRs
