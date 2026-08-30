package com.scansettle.api.tables;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ConflictException;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.openbanking.OpenBankingProvider;
import com.scansettle.api.openbanking.model.AuthorisationResult;
import com.scansettle.api.openbanking.model.PaymentInstruction;
import com.scansettle.api.payments.Payment;
import com.scansettle.api.payments.PaymentRepository;
import com.scansettle.api.payments.PaymentState;
import com.scansettle.api.payments.ProviderTransaction;
import com.scansettle.api.payments.ProviderTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the reservation pattern from docs/scansettle-tables.md / ADR-0003: a
 * {@link BillPaymentReservation} is checked-and-inserted inside one short, row-locked
 * transaction on {@link Bill} *before* any bank redirect, so a request that no longer
 * fits the remaining balance is rejected immediately rather than after a wasted trip
 * to the bank. Confirmation/failure of the resulting {@link Payment} is applied via
 * {@link BillPaymentOutcomeListener}, reusing the exact same payment/webhook
 * machinery Phase 3/4 already built for ScanSettle Links.
 */
@Service
public class BillPaymentService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillPaymentReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final VenueRepository venueRepository;
    private final OpenBankingProvider openBankingProvider;
    private final AuditService auditService;

    public BillPaymentService(BillRepository billRepository, BillPaymentRepository billPaymentRepository,
                               BillPaymentReservationRepository reservationRepository,
                               PaymentRepository paymentRepository,
                               ProviderTransactionRepository providerTransactionRepository,
                               VenueRepository venueRepository,
                               OpenBankingProvider openBankingProvider, AuditService auditService) {
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.providerTransactionRepository = providerTransactionRepository;
        this.venueRepository = venueRepository;
        this.openBankingProvider = openBankingProvider;
        this.auditService = auditService;
    }

    public record StartBillPaymentResult(Payment payment, String redirectUrl) {
    }

    @Transactional
    public StartBillPaymentResult startBillPayment(UUID billId, long contributionAmountMinorUnits,
                                                     long tipAmountMinorUnits, BillPayment.TipMethod tipMethod,
                                                     String payerContact, String idempotencyKey, String bankId) {
        if (contributionAmountMinorUnits <= 0) {
            throw new ConflictException("invalid-amount", "The amount to pay must be greater than zero.");
        }

        // The row lock below is the entire concurrency-safety mechanism (ADR-0003) —
        // no external calls happen between acquiring it and releasing it.
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new NotFoundException("Bill not found"));

        if (!bill.isOpenForPayment()) {
            throw new ConflictException("bill-not-open", "This bill is no longer accepting payments.");
        }

        long committed = billPaymentRepository.sumConfirmedContribution(billId);
        long activelyReserved = reservationRepository.sumActiveReservations(billId);
        long remaining = bill.getTotalAmountMinorUnits() - committed - activelyReserved;

        if (contributionAmountMinorUnits > remaining) {
            throw new ConflictException("insufficient-remaining-balance",
                    "Requested contribution exceeds the remaining balance — please refresh.");
        }

        BillPayment billPayment = new BillPayment(UUID.randomUUID(), billId, contributionAmountMinorUnits,
                tipAmountMinorUnits, tipMethod, payerContact);
        billPaymentRepository.save(billPayment);

        BillPaymentReservation reservation = new BillPaymentReservation(UUID.randomUUID(), billId,
                contributionAmountMinorUnits, Instant.now().plus(RESERVATION_TTL), billPayment.getId());
        reservationRepository.save(reservation);
        // Transaction (and the Bill row lock) commits here, on method return — the
        // provider call below deliberately happens outside it.

        UUID merchantId = venueRepository.findById(bill.getVenueId())
                .orElseThrow(() -> new IllegalStateException("Bill references a missing Venue: " + bill.getVenueId()))
                .getMerchantId();
        long totalAmount = billPayment.totalAmountMinorUnits();

        if (idempotencyKey != null) {
            var existing = paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
            if (existing.isPresent()) {
                return new StartBillPaymentResult(existing.get(), null);
            }
        }

        Payment payment = Payment.forBillPayment(UUID.randomUUID(), merchantId, billPayment.getId(), totalAmount,
                bill.getCurrencyCode(), payerContact, idempotencyKey);
        payment.transitionTo(PaymentState.AWAITING_PAYMENT);
        payment.transitionTo(PaymentState.REDIRECTED_TO_BANK);
        paymentRepository.save(payment);

        PaymentInstruction instruction = new PaymentInstruction(payment.getId().toString(), totalAmount,
                bill.getCurrencyCode(), "Table bill contribution", null, bankId);
        AuthorisationResult authorisation = openBankingProvider.createAuthorisation(instruction);

        ProviderTransaction providerTransaction = new ProviderTransaction(UUID.randomUUID(), payment.getId(),
                "mock", authorisation.providerReference(), "PENDING");
        providerTransactionRepository.save(providerTransaction);

        auditService.record(merchantId, AuditEvent.ActorType.CUSTOMER, payerContact, "BILL_PAYMENT_ATTEMPT_STARTED",
                "BillPayment", billPayment.getId().toString(), null,
                Map.of("billId", billId.toString(), "contributionAmountMinorUnits", contributionAmountMinorUnits,
                        "tipAmountMinorUnits", tipAmountMinorUnits));

        return new StartBillPaymentResult(payment, authorisation.redirectUrl());
    }

    /** Scheduled sweep target (see BillPaymentReservationSweeper) — frees a reservation nobody ever completed. */
    @Transactional
    public void expireReservation(BillPaymentReservation reservation) {
        reservation.expire();
        reservationRepository.save(reservation);
        billPaymentRepository.findById(reservation.getBillPaymentId()).ifPresent(bp -> {
            bp.markFailed();
            billPaymentRepository.save(bp);
        });
    }
}
