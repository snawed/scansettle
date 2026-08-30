package com.scansettle.api.merchant;

import com.scansettle.api.common.crypto.EncryptedStringConverter;
import com.scansettle.api.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_user")
public class MerchantUser {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mfa_secret_encrypted")
    private String mfaSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MerchantUser() {
        // JPA
    }

    public MerchantUser(UUID id, UUID merchantId, String email, String passwordHash, Role role) {
        this.id = id;
        this.merchantId = merchantId;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.mfaEnabled = false;
        this.status = Status.ACTIVE;
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

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Status {
        ACTIVE, DISABLED
    }
}
