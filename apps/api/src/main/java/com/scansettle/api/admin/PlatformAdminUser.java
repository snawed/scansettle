package com.scansettle.api.admin;

import com.scansettle.api.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * ScanSettle's own internal ops/support login — deliberately a separate table from
 * {@code merchant_user}, never scoped to a merchant (docs/architecture.md persona
 * "ScanSettle Ops/Support"; see {@link com.scansettle.api.security.Role#PLATFORM_ADMIN}).
 * MFA mirrors {@code MerchantUser}'s (Phase 9 hardening) — same TOTP mechanism,
 * self-enrolled, disabled by default.
 */
@Entity
@Table(name = "platform_admin_user")
public class PlatformAdminUser {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mfa_secret_encrypted")
    private String mfaSecret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlatformAdminUser() {
        // JPA
    }

    public PlatformAdminUser(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.mfaEnabled = false;
        this.createdAt = Instant.now();
    }

    public void beginMfaEnrollment(String secret) {
        this.mfaSecret = secret;
    }

    public void confirmMfaEnrollment() {
        if (mfaSecret == null) {
            throw new IllegalStateException("No MFA enrollment in progress");
        }
        this.mfaEnabled = true;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
