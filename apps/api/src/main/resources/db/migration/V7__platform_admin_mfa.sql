-- Phase 9 hardening: ops/support logins get the same TOTP MFA option merchant
-- users already have (V3), self-enrolled, disabled by default so the seeded
-- account keeps logging in without a code until an ops admin turns it on.
alter table platform_admin_user add column mfa_enabled boolean not null default false;
alter table platform_admin_user add column mfa_secret_encrypted varchar(255);
