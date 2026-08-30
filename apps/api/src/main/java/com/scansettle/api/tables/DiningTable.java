package com.scansettle.api.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Maps to the {@code dining_table} table — "table" is a reserved SQL word. */
@Entity
@Table(name = "dining_table")
public class DiningTable {

    public enum Status {
        ACTIVE, INACTIVE
    }

    /** Whether a party is currently seated with an open bill — distinct from {@link Status}, which
     * just means "is this table enabled in the system at all". Drives what a QR scan serves. */
    public enum OccupancyStatus {
        FREE, OCCUPIED
    }

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(nullable = false)
    private String label;

    @Column(name = "qr_token", nullable = false, unique = true)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_status", nullable = false)
    private OccupancyStatus occupancyStatus;

    protected DiningTable() {
        // JPA
    }

    public DiningTable(UUID id, UUID venueId, String label) {
        this.id = id;
        this.venueId = venueId;
        this.label = label;
        this.qrToken = UUID.randomUUID().toString();
        this.status = Status.ACTIVE;
        this.occupancyStatus = OccupancyStatus.FREE;
    }

    public void occupy() {
        this.occupancyStatus = OccupancyStatus.OCCUPIED;
    }

    public void free() {
        this.occupancyStatus = OccupancyStatus.FREE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public String getLabel() {
        return label;
    }

    public String getQrToken() {
        return qrToken;
    }

    public Status getStatus() {
        return status;
    }

    public OccupancyStatus getOccupancyStatus() {
        return occupancyStatus;
    }
}
