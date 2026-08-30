package com.scansettle.api.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Display/reconciliation only — customers pay against the bill total, not individual items. */
@Entity
@Table(name = "bill_line_item")
public class BillLineItem {

    @Id
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(nullable = false)
    private String description;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    protected BillLineItem() {
        // JPA
    }

    public BillLineItem(UUID id, UUID billId, String description, long amountMinorUnits) {
        this.id = id;
        this.billId = billId;
        this.description = description;
        this.amountMinorUnits = amountMinorUnits;
    }

    public void update(String description, long amountMinorUnits) {
        this.description = description;
        this.amountMinorUnits = amountMinorUnits;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBillId() {
        return billId;
    }

    public String getDescription() {
        return description;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }
}
