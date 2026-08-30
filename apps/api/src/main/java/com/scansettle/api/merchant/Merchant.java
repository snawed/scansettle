package com.scansettle.api.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant")
public class Merchant {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(name = "pricing_plan_id", nullable = false)
    private UUID pricingPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Merchant() {
        // JPA
    }

    public Merchant(UUID id, String legalName, String tradingName, String businessType, UUID pricingPlanId) {
        this.id = id;
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.businessType = businessType;
        this.pricingPlanId = pricingPlanId;
        this.verificationStatus = VerificationStatus.UNVERIFIED;
        this.status = MerchantStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void updateProfile(String tradingName, String businessType) {
        this.tradingName = tradingName;
        this.businessType = businessType;
    }

    public void suspend() {
        this.status = MerchantStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = MerchantStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradingName() {
        return tradingName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public UUID getPricingPlanId() {
        return pricingPlanId;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum VerificationStatus {
        UNVERIFIED, PENDING, VERIFIED, REJECTED
    }

    public enum MerchantStatus {
        ACTIVE, SUSPENDED
    }
}
