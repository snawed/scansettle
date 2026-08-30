package com.scansettle.api.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bill")
public class Bill {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "pos_reference")
    private String posReference;

    @Column(name = "total_amount_minor_units", nullable = false)
    private long totalAmountMinorUnits;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillState state;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Bill() {
        // JPA
    }

    public Bill(UUID id, UUID venueId, UUID tableId, long totalAmountMinorUnits, String currencyCode) {
        this.id = id;
        this.venueId = venueId;
        this.tableId = tableId;
        this.totalAmountMinorUnits = totalAmountMinorUnits;
        this.currencyCode = currencyCode;
        this.state = BillState.OPEN;
        this.openedAt = Instant.now();
    }

    /** Recomputes OPEN/PARTIALLY_PAID/PAID from the true committed total — never overwrites a VOIDED bill. */
    public void reflectCommittedTotal(long committedTotalMinorUnits) {
        if (state == BillState.VOIDED) {
            return;
        }
        if (committedTotalMinorUnits >= totalAmountMinorUnits) {
            state = BillState.PAID;
            closedAt = Instant.now();
        } else if (committedTotalMinorUnits > 0) {
            state = BillState.PARTIALLY_PAID;
        } else {
            state = BillState.OPEN;
        }
    }

    /** Recomputed from the line items after any add/amend/remove — see BillController. */
    public void setTotalAmountMinorUnits(long totalAmountMinorUnits) {
        this.totalAmountMinorUnits = totalAmountMinorUnits;
    }

    public void voidBill() {
        this.state = BillState.VOIDED;
        this.closedAt = Instant.now();
    }

    public boolean isOpenForPayment() {
        return state == BillState.OPEN || state == BillState.PARTIALLY_PAID;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public String getPosReference() {
        return posReference;
    }

    public long getTotalAmountMinorUnits() {
        return totalAmountMinorUnits;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BillState getState() {
        return state;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
