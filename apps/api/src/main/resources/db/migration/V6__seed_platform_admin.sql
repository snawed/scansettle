-- Dev/test bootstrap ops account — there is no self-registration for platform
-- admins (unlike merchants), so at least one has to exist to log in at all.
-- Credential is a placeholder for local/dev use only: ops@scansettle.dev /
-- OpsPassword123! — rotate or remove before any real deployment (Phase 9 hardening
-- covers proper ops-account provisioning).
insert into platform_admin_user (email, password_hash)
values ('ops@scansettle.dev', '$2a$10$bt/rdsEqUsQrpTGya85DhetrKYZOfupniftGAm9pZX6DzcjmhO8ia');
