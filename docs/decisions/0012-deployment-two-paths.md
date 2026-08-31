# ADR-0012: Two documented deployment paths — non-prod now, prod deferred

Status: Accepted — non-prod path built (Terraform + GitHub Actions OIDC,
docs/deployment.md Section 1); production path still planning-only.
Date: 2026-08-30

## Context

Phase 10 (Deployment/IaC) was scoped by architecture.md as "Terraform, from
Phase 10" with no further detail. The user has a GitHub account and AWS free
tier available, wants something usable for testing soon, and separately, does
not want that testing setup mistaken for — or quietly grown into — a production
deployment. Those are two different sets of requirements (cost ceiling and
resilience needs are opposite), not one deployment scoped down.

## Decision

Document both paths fully before building either (`docs/deployment.md`):

- **Non-production**: a single EC2 instance running the existing
  `infra/docker-compose.yml` unmodified, Terraform-provisioned, GitHub Actions
  deploying over SSH/SSM. Free-tier-bounded, explicitly disposable —
  `terraform destroy` is the expected normal state between testing sessions,
  not a fallback.
- **Production**: ECS Fargate behind an ALB, RDS Postgres Multi-AZ, Secrets
  Manager, a real VPC (public/private subnets across AZs), WAF, GitHub Actions
  deploying via OIDC + rolling ECS updates (no SSH, no stored AWS keys in
  GitHub). Not free-tier; not being built yet.

Both paths run the exact same two container images (`apps/api/Dockerfile`,
`apps/web/Dockerfile`) unmodified — the split is entirely in the infrastructure
around them, not the application. This matters: it means starting with the
non-prod path creates no technical debt that blocks moving to the prod path
later, and no application code needs to anticipate either environment.

## Consequences

- The user picks which path (or both, sequenced) to actually build next — this
  ADR and `docs/deployment.md` are the reference for that follow-up work, not
  the work itself.
- `docs/architecture.md`'s existing Section 14 risk "Single Postgres instance as
  a scaling/availability bottleneck ... revisit at Phase 10" is resolved by this
  ADR's production path (RDS Multi-AZ) — still open until that path is actually
  built, not before.
- Neither path requires a real `{{OPEN_BANKING_PROVIDER}}` to be named — Phase 5
  stays parked independently of Phase 10's infrastructure work.

## Addendum: two silent OIDC trust-policy traps (2026-08-31)

Getting the non-prod path's first real `terraform apply` to actually run
surfaced two AWS OIDC gotchas worth recording, since both produce the exact
same generic `Not authorized to perform sts:AssumeRoleWithWebIdentity` error
with no distinguishing detail — every other part of the setup (OIDC provider,
audience, thumbprint, trust policy `Principal`, no SCP) can be completely
correct and the assume-role call still fails silently for either reason:

1. **`aws-actions/configure-aws-credentials` tags the assumed session** with
   GitHub context by default — the trust policy's `Action` must include
   `sts:TagSession` alongside `sts:AssumeRoleWithWebIdentity`, or grant
   `role-skip-session-tagging: true` on the action's inputs instead (the
   route `infra/aws-bootstrap/bootstrap.sh` and both workflows now take —
   simpler than granting the extra permission, and session tags aren't
   needed for this environment).
2. **GitHub's OIDC `sub` claim comes in two formats**: the classic
   `repo:OWNER/REPO:ref:...` and, apparently now the default for at least
   some accounts, an ID-qualified `repo:OWNER@id/REPO@id:ref:...` — a
   hardening feature so a trust policy stays valid (and doesn't silently
   start trusting an attacker) across a repo or account rename. A trust
   policy's `StringLike` condition matching only the classic format never
   matches this ID-qualified form. `bootstrap.sh` now allows both patterns.

Diagnosed by temporarily adding a workflow step that requests and decodes the
actual OIDC token's claims directly (bypassing the wrapped action's opaque
error) — worth reaching for immediately next time rather than working through
the AWS-side checklist first, since the AWS side can be entirely correct.
