package com.scansettle.api.payments;

import com.scansettle.api.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to pay a {@link PaymentLink}. State transitions are validated against
 * {@link PaymentState}'s allowed-transitions map — illegal transitions are rejected,
 * not silently applied (docs/payment-states.md).
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_link_id")
    private UUID paymentLinkId;

    @Column(name = "bill_payment_id")
    private UUID billPaymentId;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentState state;

    @Column(name = "payer_contact")
    private String payerContact;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // JPA
    }

    private Payment(UUID id, UUID merchantId, UUID paymentLinkId, UUID billPaymentId, long amountMinorUnits,
                     String currencyCode, String payerContact, String idempotencyKey) {
        this.id = id;
        this.merchantId = merchantId;
        this.paymentLinkId = paymentLinkId;
        this.billPaymentId = billPaymentId;
        this.amountMinorUnits = amountMinorUnits;
        this.currencyCode = currencyCode;
        this.state = PaymentState.CREATED;
        this.payerContact = payerContact;
        this.idempotencyKey = idempotencyKey;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A one-off attempt to pay a {@link PaymentLink}. */
    public Payment(UUID id, UUID merchantId, UUID paymentLinkId, long amountMinorUnits, String currencyCode,
                    String payerContact, String idempotencyKey) {
        this(id, merchantId, paymentLinkId, null, amountMinorUnits, currencyCode, payerContact, idempotencyKey);
    }

    /** A single customer's contribution+tip attempt against a ScanSettle Tables {@code BillPayment}. */
    public static Payment forBillPayment(UUID id, UUID merchantId, UUID billPaymentId, long amountMinorUnits,
                                          String currencyCode, String payerContact, String idempotencyKey) {
        return new Payment(id, merchantId, null, billPaymentId, amountMinorUnits, currencyCode, payerContact,
                idempotencyKey);
    }

    public void transitionTo(PaymentState target) {
        if (!this.state.canTransitionTo(target)) {
            throw new ConflictException("illegal-payment-state-transition",
                    "Cannot move payment from " + this.state + " to " + target);
        }
        this.state = target;
        this.updatedAt = Instant.now();
    }

    /** Idempotent: re-applying the same terminal-bound status a payment already reached is a no-op. */
    public boolean isAlreadyIn(PaymentState target) {
        return this.state == target;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getPaymentLinkId() {
        return paymentLinkId;
    }

    public UUID getBillPaymentId() {
        return billPaymentId;
    }

    public long getAmountMinorUnits() {
        return amountMinorUnits;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public PaymentState getState() {
        return state;
    }

    public String getPayerContact() {
        return payerContact;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
