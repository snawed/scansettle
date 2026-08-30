-- MFA secret storage for merchant_user, needed once real login/MFA enrollment
-- (Phase 3) exists. Encrypted the same way as bank account fields — see
-- EncryptedStringConverter.
alter table merchant_user add column mfa_secret_encrypted varchar(255);
