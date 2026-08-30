# ScanSettle — Deployment (Phase 10)

Status: Planning document — two deployment paths documented for pickup, neither
built yet. See ADR-0012 for the decision behind splitting them.

This document is a runbook, not finished IaC: it describes exactly what to
provision and in what order, with illustrative Terraform/config snippets to
clarify shape, not copy-paste-ready modules. Building either path is separate
follow-up work.

## 0. What ScanSettle needs from any environment

Both paths provision the same underlying requirements — worth stating once:

- Two containers already built by `apps/api/Dockerfile` and `apps/web/Dockerfile`
  (multi-stage, non-root, reviewed in `docs/security-audit-phase9.md`).
- One PostgreSQL 16 database (Flyway owns the schema — nothing manual to run).
- Environment variables the API reads (`apps/api/src/main/resources/application.yml`):
  `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `APP_JWT_SECRET` (≥32 bytes),
  `APP_ENCRYPTION_KEY` (base64, decodes to 32 bytes — AES-256 for bank details/MFA
  secrets), `APP_CORS_ALLOWED_ORIGINS`, `APP_MOCK_BANK_BASE_URL`,
  `APP_LINKS_BASE_URL`, and the `OPEN_BANKING_*` placeholders (unused while
  `OPEN_BANKING_PROVIDER=mock` — Phase 5 is still parked).
- One build-time variable the web app needs: `NEXT_PUBLIC_API_BASE_URL` (baked
  into the client bundle — the *browser's* address for the API) plus one runtime
  variable, `API_BASE_URL` (the *server-side* address, used by Next.js Server
  Components — see `apps/web/Dockerfile`'s comment on why these differ).
- Nothing else stateful — no file storage, no queue, no cache. QR codes are
  generated on demand (`docs/api.md`), never persisted.

Both paths use GitHub Actions (already the CI runner — `.github/workflows/ci.yml`)
to build and push images; the two paths differ in what happens after that.

---

## 1. Non-production / testing — EC2 + Docker Compose

**Goal**: a single, cheap, disposable environment for demos and manual testing —
not resilient, not multi-AZ, explicitly not meant to hold real merchant data.
Reuses `infra/docker-compose.yml` almost as-is.

### 1.1 Prerequisites

- An AWS account with the free tier active (new-ish account, or one that hasn't
  exhausted the 12-month free tier clock).
- An IAM user or role for Terraform with programmatic access — least-privilege
  scoped to EC2, VPC, IAM (to create the instance role), and SSM Parameter Store.
  Do not use root account credentials.
- A GitHub repo secret holding those credentials (or better, GitHub's OIDC
  provider for AWS — no long-lived key in GitHub at all; see 1.6).
- Terraform installed locally for the first `apply` (state bootstrap), then CI
  can take over.

### 1.2 Architecture

```
GitHub Actions ──push image──> ECR (or Docker Hub)
       │
       └──ssh deploy──> EC2 (t3.micro/t2.micro, public subnet, Elastic IP)
                              │
                              └── docker compose: web:3000, api:8080, postgres:5432
                                  (all three containers on one box)
```

One instance, one Elastic IP, security group open on 80/443 (or just 3000/8080
for testing without a domain) and 22 (SSH, ideally restricted to a known IP or
via AWS Systems Manager Session Manager instead of open SSH).

### 1.3 Step-by-step

1. **Terraform state backend** (one-time, by hand or a tiny bootstrap script):
   an S3 bucket (versioned, encrypted) + a DynamoDB table for the state lock.
   Free tier covers this trivially at ScanSettle's state-file size.
2. **VPC**: either a minimal custom VPC (one public subnet, one route table, one
   internet gateway) or just use the account's default VPC — for a non-prod
   single-box environment, the default VPC is a legitimate, simpler choice.
3. **Security group**: inbound 22 (SSH — restrict to your IP or use SSM Session
   Manager and drop this rule entirely), 3000 and 8080 (or 80/443 if a domain +
   reverse proxy is added later), outbound all.
4. **IAM instance role**: minimal — SSM managed instance core policy (for Session
   Manager access without SSH) and permission to read the SSM Parameter Store
   parameters this app needs (see 1.5).
5. **EC2 instance**: `t3.micro` (free tier: 750 hrs/month for 12 months),
   Amazon Linux 2023 or Ubuntu, an Elastic IP attached. `user_data` installs
   Docker + the Docker Compose plugin and clones/pulls the deploy directory —
   or, simpler, GitHub Actions pushes a rendered `docker-compose.yml` and
   `.env` file to the instance on every deploy (see 1.6).
6. **Secrets**: put `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY`, and the Postgres
   password in **SSM Parameter Store** as `SecureString`s (free, unlike Secrets
   Manager's per-secret charge) — the instance role reads them at container
   start via a small entrypoint script, or Terraform/CI injects them into a
   `.env` file that's never committed.
7. **DNS (optional for this path)**: skip it, or a single Route53 `A` record to
   the Elastic IP if a domain is already owned — not required for internal
   testing (use the Elastic IP directly).
8. **GitHub Actions deploy job** (append to `.github/workflows/ci.yml` or a new
   `deploy.yml`, triggered on push to `main` after CI passes):
   - Build and push both images to ECR (a `docker/build-push-action` step per
     image, tagged with the git SHA).
   - SSH (via an ephemeral key, or SSM `send-command` to avoid opening port 22
     at all) to the EC2 instance: `docker compose pull && docker compose up -d`.
9. **First deploy & smoke test**: hit `http://<elastic-ip>:8080/actuator/health`
   and `http://<elastic-ip>:3000/`, then run through the same manual checks used
   throughout this build (register a merchant, log in, create a payment link,
   pay via the mock bank) against the live instance.

### 1.4 Cost & free-tier guardrails

- `t3.micro`/`t2.micro`: free for 750 hrs/month for 12 months from account
  creation — one instance running continuously stays exactly at that limit, so
  never run a second one in parallel without stopping the first.
- EBS: free tier covers 30GB — the default 8-20GB root volume is well within it.
- Data transfer: free tier covers 100GB/month out — a testing environment won't
  come close.
- Set a **AWS Budget alert** (free) at a low dollar threshold (e.g. $1) as a
  tripwire the moment anything drifts outside free tier.

### 1.5 Teardown

`terraform destroy` removes the instance, EIP, security group, and IAM role in
one command — the whole point of provisioning this via Terraform rather than
clicking through the console is that "throw it away and rebuild" is free and
fast. Do this whenever the testing environment isn't actively in use, since an
EIP not attached to a running instance is billed even on free tier.

---

## 2. Production — ECS Fargate + RDS + ALB

**Goal**: multi-AZ, no single point of failure, real backups, TLS end-to-end,
rolling deploys with no manual SSH step, secrets properly rotated. This is
**not** a free-tier target — real cost, appropriate once real merchant funds are
moving.

### 2.1 Architecture

```
                         Route53 (domain)
                              │
                         ACM cert (TLS)
                              │
                    Application Load Balancer  (public subnets, 2+ AZs)
                        │              │
                 target group:web  target group:api
                        │              │
                 ECS Fargate       ECS Fargate        (private subnets, 2+ AZs)
                 service: web      service: api
                                        │
                                  RDS Postgres
                                  Multi-AZ           (private subnets, 2+ AZs)

GitHub Actions (OIDC, no stored keys) → ECR → ECS rolling deploy
Secrets Manager → injected into ECS task definitions at runtime
CloudWatch Logs + Alarms ← both services
WAF ← attached to the ALB
```

### 2.2 Step-by-step

1. **VPC**: a real one this time — 2 (ideally 3) Availability Zones, public
   subnets for the ALB, private subnets for ECS tasks and RDS, a NAT Gateway
   (or NAT instance to save cost) so private-subnet tasks can still reach ECR/
   the internet for pulls without being internet-routable themselves.
2. **RDS Postgres, Multi-AZ**: automated backups (7-35 day retention), storage
   encryption at rest (already a docs/security.md requirement), a parameter
   group enforcing `rds.force_ssl=1` so the API connects over TLS. Sits in the
   private subnets only — no public accessibility, ever.
3. **Secrets Manager**: `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY`, the RDS master
   credentials (RDS can auto-manage this via "Manage master credentials in
   Secrets Manager" at creation), and — once Phase 5 names a real
   `{{OPEN_BANKING_PROVIDER}}` — its client secret and webhook signing secret.
   Enable automatic rotation where the target supports it (RDS credentials do
   natively).
4. **ECR**: two repositories, `scansettle-api` and `scansettle-web`, with a
   lifecycle policy to expire untagged/old images so storage doesn't grow
   unbounded.
5. **ECS cluster** (Fargate launch type — no EC2 instances to patch):
   - Task definitions for `api` and `web`, each pulling secrets from Secrets
     Manager directly into the container environment (no secret ever touches a
     Terraform state file or a GitHub Actions log).
   - Services with a minimum of 2 tasks each, spread across AZs, behind the ALB.
   - Auto-scaling policies on CPU/memory (or request count for the ALB target
     group) — start conservative (e.g. 2-4 tasks) and tune from real traffic.
6. **ALB**: HTTPS listener (443) using an ACM certificate for the domain, HTTP
   (80) listener that redirects to HTTPS. Two target groups (web, api) routed
   by path or by separate subdomains (`app.scansettle.com` /
   `api.scansettle.com`) — a subdomain split matches how `NEXT_PUBLIC_API_BASE_URL`
   already expects a distinct origin for the API.
7. **Route53**: `A`/`ALIAS` records pointing the domain(s) at the ALB.
8. **WAF**: attach AWS Managed Rules (common rule set + known bad inputs) to the
   ALB — a real, internet-facing product needs this in a way the non-prod path
   doesn't.
9. **Security groups**: ALB accepts 443/80 from the internet; ECS tasks accept
   traffic *only* from the ALB's security group; RDS accepts traffic *only*
   from the ECS tasks' security group. No component the internet can reach
   directly except the ALB.
10. **GitHub Actions deploy**, via OIDC federation (GitHub's
    `aws-actions/configure-aws-credentials` with `role-to-assume`, no stored AWS
    keys in GitHub at all):
    - Build, tag (git SHA + `latest`), push to ECR.
    - Update the ECS task definition with the new image tag, then
      `aws ecs update-service --force-new-deployment` — ECS handles the rolling
      replacement (new tasks healthy before old ones drain), so a bad deploy
      never takes the service fully down.
11. **CloudWatch**: log groups per service (the app already emits structured,
    correlation-ID-tagged logs — `docs/security.md` — so CloudWatch Logs
    Insights queries by `correlationId` work immediately with no extra plumbing).
    Alarms on: ECS service unhealthy-task count, ALB 5xx rate, RDS CPU/storage/
    connections, and a billing alarm.
12. **Terraform structure**: separate state per environment (`envs/staging`,
    `envs/prod`, sharing modules under `modules/vpc`, `modules/ecs-service`,
    `modules/rds`) rather than one flat config — lets staging validate a change
    before it touches prod, and keeps state files from becoming a single
    blast-radius.

### 2.3 Backup / disaster recovery

- RDS automated backups + at least one manual snapshot before any risky schema
  migration.
- Cross-region snapshot copy if the product ever needs a DR story beyond
  single-region Multi-AZ (not needed at MVP scale, worth planning for).
- ECS/Fargate itself needs no backup — it's stateless; redeploying from ECR is
  the recovery path for the compute layer.

### 2.4 Cost shape (directional, not a quote)

Dominant costs at low traffic: RDS Multi-AZ instance (biggest single line item),
NAT Gateway (charged per-hour + per-GB even at idle), ALB (per-hour + per-LCU).
Fargate task cost scales with actual usage. None of this is free-tier-eligible
at meaningful scale — budgeting this properly is part of the Phase 10 decision,
not an afterthought.

---

## 3. Migration path, when the time comes

Nothing here requires re-architecting the app — both paths run the exact same
two container images unmodified. Moving from the non-prod EC2 environment to
the production ECS environment is: stand up the production Terraform stack
independently (steps in Section 2), point GitHub Actions' deploy job at it
instead of (or in addition to) the EC2 target, cut over DNS once verified, then
tear down the non-prod EC2 environment or keep it as a separate testing lane
permanently — either is fine, they don't share state.
