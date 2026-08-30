# ScanSettle — Deployment (Phase 10)

Status: **Non-production path built** (Terraform + GitHub Actions workflows —
Section 1 below is now a runbook for real code, not a sketch). Production
(Section 2) is still planning-only. See ADR-0012 for the decision behind
splitting them.

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

**Status: built.** What follows is the actual runbook, not a sketch:

- `infra/aws-bootstrap/bootstrap.sh` — one-time AWS CLI script (S3 state bucket,
  DynamoDB lock table, GitHub OIDC provider, IAM deploy role — least-privilege,
  not AdministratorAccess).
- `infra/terraform/nonprod/*.tf` — the actual EC2/VPC/security-group/IAM-instance-role/
  ECR/SSM-parameters module. `terraform validate` clean.
- `.github/workflows/terraform-nonprod.yml` — OIDC-authenticated plan/apply,
  manually triggered (or plans automatically on a PR touching this path).
- `.github/workflows/deploy-nonprod.yml` — builds + pushes both images to ECR,
  then redeploys via AWS Systems Manager (no SSH) once CI passes on `main`.
- `infra/docker-compose.nonprod.yml` — the compose file actually deployed to the
  instance (pulls pre-built ECR images, unlike `infra/docker-compose.yml` which
  builds from source for local dev).

### 1.1 Architecture (as built)

```
GitHub Actions (OIDC, no stored keys)
       │
       ├─ terraform-nonprod.yml ──apply──> EC2 + VPC/SG/IAM + ECR + SSM params
       │
       └─ deploy-nonprod.yml ──build+push──> ECR
                              ──upload──────> S3 (deploy-artifacts/)
                              ──ssm send-command──> EC2 instance
                                                       │
                                                       └─ docker compose: web:3000, api:8080, postgres:5432
```

One instance, one Elastic IP (tagged `Name=scansettle-nonprod`, looked up by
tag rather than hardcoded), security group open on 3000/8080 only — **no port
22**. Operator shell access, if ever needed, goes through AWS Systems Manager
Session Manager (`aws ssm start-session --target <instance-id>`), which the
instance's IAM role already grants — never SSH.

### 1.2 One-time setup (you do this)

1. **Bootstrap AWS** — open **AWS CloudShell** (console.aws.amazon.com → the
   `>_` icon, top right, region **eu-west-2**) so nothing needs installing
   locally. Upload `infra/aws-bootstrap/bootstrap.sh`, then:
   ```bash
   chmod +x bootstrap.sh && ./bootstrap.sh
   ```
   It prints an IAM role ARN and three config values at the end.
2. **Add them to GitHub** (`github.com/snawed/scansettle/settings`):
   - **Secrets** (Secrets and variables → Actions → Secrets tab):
     `AWS_DEPLOY_ROLE_ARN` = the role ARN bootstrap.sh printed.
   - **Variables** (same page → Variables tab):
     `AWS_REGION` = `eu-west-2`, `TF_STATE_BUCKET` and `TF_LOCK_TABLE` = the
     values bootstrap.sh printed.
3. **Provision the infrastructure** — GitHub → Actions tab → "Terraform
   (non-prod)" → Run workflow → `action: apply`. First run takes a few minutes
   (EC2 boots, installs Docker via `user_data`). Note the `public_ip` output.
4. **First deploy** — GitHub → Actions tab → "Deploy (non-prod)" → Run workflow
   (or just push to `main` — it also runs automatically once CI passes).

### 1.3 Smoke test

Once the deploy workflow finishes (its summary prints the URLs):
- `http://<public-ip>:8080/actuator/health` → `{"status":"UP",...}`
- `http://<public-ip>:3000/` → the ScanSettle landing page
- Run through the same manual checks used throughout this build: register a
  merchant, log in, create a payment link, pay via the mock bank.

### 1.4 Redeploying after a code change

Nothing manual — push to `main`, CI runs, and `deploy-nonprod.yml` triggers
automatically once CI succeeds, rebuilding and rolling both images. Infra
changes (editing `infra/terraform/nonprod/*.tf`) need a separate, deliberate
"Terraform (non-prod)" → `apply` run — infra changes are never auto-applied on
push, only planned (see the workflow's PR-comment behaviour).

### 1.5 Cost & free-tier guardrails

- `t3.micro`/`t2.micro`: free for 750 hrs/month for 12 months from account
  creation — one instance running continuously stays exactly at that limit, so
  never run a second one in parallel without stopping the first.
- EBS: free tier covers 30GB — the default 8-20GB root volume is well within it.
- Data transfer: free tier covers 100GB/month out — a testing environment won't
  come close.
- Set a **AWS Budget alert** (free) at a low dollar threshold (e.g. $1) as a
  tripwire the moment anything drifts outside free tier.

### 1.6 Teardown

Run the "Terraform (non-prod)" workflow won't destroy — that action isn't
wired up on purpose (destroy is rarer and riskier than plan/apply, and this
workflow only supports `plan`/`apply`). To tear down: from the same directory
locally (`cd infra/terraform/nonprod && terraform init -backend-config=...`
with the values from bootstrap.sh's output, then `terraform destroy`) — or add
a `destroy` option to the workflow's `action` input later if teardown becomes
routine. Removes the instance, EIP, security group, IAM role, and ECR repos in
one command. Do this whenever the testing environment isn't actively in use,
since an EIP not attached to a running instance is billed even on free tier.

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
