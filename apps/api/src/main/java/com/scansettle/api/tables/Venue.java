package com.scansettle.api.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A hospitality merchant's site — one merchant may operate several (ADR-0002). */
@Entity
@Table(name = "venue")
public class Venue {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Venue() {
        // JPA
    }

    public Venue(UUID id, UUID merchantId, String name, String address, String timezone) {
        this.id = id;
        this.merchantId = merchantId;
        this.name = name;
        this.address = address;
        this.timezone = timezone == null ? "Europe/London" : timezone;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
