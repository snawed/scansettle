package com.scansettle.api.merchant;

import com.scansettle.api.common.crypto.EncryptedStringConverter;
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
@Table(name = "merchant_bank_account")
public class MerchantBankAccount {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "sort_code_encrypted", nullable = false)
    private String sortCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number_encrypted", nullable = false)
    private String accountNumber;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(nullable = false)
    private boolean verified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MerchantBankAccount() {
        // JPA
    }

    public MerchantBankAccount(UUID id, UUID merchantId, String sortCode, String accountNumber, String accountName) {
        this.id = id;
        this.merchantId = merchantId;
        this.sortCode = sortCode;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.verified = false;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void markReplaced() {
        this.status = Status.REPLACED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getSortCode() {
        return sortCode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    /** Last 4 digits only — what the UI shows; the full number never round-trips to a browser. */
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public boolean isVerified() {
        return verified;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Status {
        ACTIVE, REPLACED
    }
}
