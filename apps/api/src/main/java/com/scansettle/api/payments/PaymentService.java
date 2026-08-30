package com.scansettle.api.payments;

import com.scansettle.api.audit.AuditEvent;
import com.scansettle.api.audit.AuditService;
import com.scansettle.api.common.error.ApplicationException;
import com.scansettle.api.common.error.ConflictException;
import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.merchant.Merchant;
import com.scansettle.api.merchant.MerchantRepository;
import com.scansettle.api.openbanking.OpenBankingProvider;
import com.scansettle.api.openbanking.model.AuthorisationResult;
import com.scansettle.api.openbanking.model.PaymentInstruction;
import com.scansettle.api.openbanking.model.PaymentStatusResult;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import com.scansettle.api.pricing.FeeCalculator;
import com.scansettle.api.pricing.PricingPlanRepository;
import com.scansettle.api.reconciliation.ReconciliationRecord;
import com.scansettle.api.reconciliation.ReconciliationRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    /**
     * Maps the provider's own status vocabulary onto ScanSettle's payment state
     * machine. Deliberately has NO entry for {@link ProviderPaymentStatus#PENDING} —
     * that value means "nothing has happened at the bank yet" (the provider's
     * initial/default status), which is a different concept from our own
     * {@link PaymentState#PAYMENT_PENDING} ("submitted, awaiting settlement
     * confirmation"). Mapping it would make every status poll before the customer
     * has done anything at the bank incorrectly advance the state machine — the
     * absent entry means {@code PROVIDER_STATUS_MAPPING.get(PENDING)} returns
     * {@code null}, which the caller correctly treats as "nothing to apply".
     */
    private static final Map<ProviderPaymentStatus, PaymentState> PROVIDER_STATUS_MAPPING = Map.of(
            ProviderPaymentStatus.SUBMITTED, PaymentState.PAYMENT_SUBMITTED,
            ProviderPaymentStatus.CONFIRMED, PaymentState.PAYMENT_CONFIRMED,
            ProviderPaymentStatus.REJECTED, PaymentState.REJECTED,
            ProviderPaymentStatus.FAILED, PaymentState.FAILED,
            ProviderPaymentStatus.CANCELLED, PaymentState.CANCELLED,
            ProviderPaymentStatus.EXPIRED, PaymentState.EXPIRED);

    private static final Set<PaymentState> RECONCILIABLE_STATES = Set.of(
            PaymentState.PAYMENT_CONFIRMED, PaymentState.FAILED, PaymentState.REJECTED);

    private final PaymentRepository paymentRepository;
    private final PaymentLinkRepository paymentLinkRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final FeeLedgerEntryRepository feeLedgerEntryRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final MerchantRepository merchantRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final FeeCalculator feeCalculator;
    private final OpenBankingProvider openBankingProvider;
    private final AuditService auditService;
    private final List<PaymentOutcomeListener> outcomeListeners;

    public PaymentService(PaymentRepository paymentRepository, PaymentLinkRepository paymentLinkRepository,
                           ProviderTransactionRepository providerTransactionRepository,
                           FeeLedgerEntryRepository feeLedgerEntryRepository,
                           ReconciliationRecordRepository reconciliationRecordRepository,
                           MerchantRepository merchantRepository,
                           PricingPlanRepository pricingPlanRepository, FeeCalculator feeCalculator,
                           OpenBankingProvider openBankingProvider, AuditService auditService,
                           List<PaymentOutcomeListener> outcomeListeners) {
        this.paymentRepository = paymentRepository;
        this.paymentLinkRepository = paymentLinkRepository;
        this.providerTransactionRepository = providerTransactionRepository;
        this.feeLedgerEntryRepository = feeLedgerEntryRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.merchantRepository = merchantRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.feeCalculator = feeCalculator;
        this.openBankingProvider = openBankingProvider;
        this.auditService = auditService;
        this.outcomeListeners = outcomeListeners;
    }

    public record StartPaymentResult(Payment payment, String redirectUrl) {
    }

    @Transactional
    public StartPaymentResult startPayment(UUID linkId, String idempotencyKey, String payerContact, String bankId) {
        PaymentLink link = paymentLinkRepository.findById(linkId)
                .orElseThrow(() -> new NotFoundException("Payment link not found"));

        if (idempotencyKey != null) {
            var existing = paymentRepository.findByMerchantIdAndIdempotencyKey(link.getMerchantId(), idempotencyKey);
            if (existing.isPresent()) {
                // Replaying a known Idempotency-Key returns the existing attempt without
                // calling the provider again; the original redirect URL was single-use
                // and is not persisted (docs/api.md — idempotency), so it comes back null.
                return new StartPaymentResult(existing.get(), null);
            }
        }

        if (!link.isPayable()) {
            throw new ConflictException("payment-link-not-payable", "This payment link is no longer active.");
        }

        Payment payment = new Payment(UUID.randomUUID(), link.getMerchantId(), link.getId(),
                link.getAmountMinorUnits(), link.getCurrencyCode(), payerContact, idempotencyKey);
        payment.transitionTo(PaymentState.AWAITING_PAYMENT);
        payment.transitionTo(PaymentState.REDIRECTED_TO_BANK);
        paymentRepository.save(payment);

        PaymentInstruction instruction = new PaymentInstruction(
                payment.getId().toString(), payment.getAmountMinorUnits(), payment.getCurrencyCode(),
                link.getDescription(), null, bankId);
        AuthorisationResult authorisation = openBankingProvider.createAuthorisation(instruction);

        ProviderTransaction providerTransaction = new ProviderTransaction(
                UUID.randomUUID(), payment.getId(), "mock", authorisation.providerReference(), "PENDING");
        providerTransactionRepository.save(providerTransaction);

        auditService.record(payment.getMerchantId(), AuditEvent.ActorType.CUSTOMER, payerContact,
                "PAYMENT_ATTEMPT_STARTED", "Payment", payment.getId().toString(), null,
                Map.of("amountMinorUnits", payment.getAmountMinorUnits(), "linkId", link.getId().toString()));

        return new StartPaymentResult(payment, authorisation.redirectUrl());
    }

    /** Public status poll — also the (temporary, pre-webhook) mechanism that syncs state from the provider. */
    @Transactional
    public Payment syncAndGetStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));

        if (payment.getState().isTerminal()) {
            return payment;
        }

        ProviderTransaction providerTransaction = providerTransactionRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment has no provider transaction: " + paymentId));

        PaymentStatusResult statusResult = openBankingProvider.getPaymentStatus(providerTransaction.getProviderReference());
        PaymentState mapped = PROVIDER_STATUS_MAPPING.get(statusResult.status());
        if (mapped != null && mapped != payment.getState()) {
            applyStateChange(payment, providerTransaction, mapped, statusResult.status().name());
        }

        return payment;
    }

    public enum WebhookApplicationResult {
        APPLIED, ALREADY_TERMINAL, NO_MATCHING_PAYMENT
    }

    /**
     * The real path (Phase 4): a verified inbound webhook reports a provider status
     * for a given provider reference. Called only after
     * {@link OpenBankingWebhookController} has confirmed the webhook's signature and
     * freshness and deduplicated it against {@link com.scansettle.api.openbanking.WebhookEvent} —
     * this method still checks the payment isn't already terminal as defense in depth.
     */
    @Transactional
    public WebhookApplicationResult applyWebhookStatus(String providerReference, ProviderPaymentStatus status) {
        Optional<ProviderTransaction> providerTransaction =
                providerTransactionRepository.findByProviderReference(providerReference);
        if (providerTransaction.isEmpty()) {
            return WebhookApplicationResult.NO_MATCHING_PAYMENT;
        }

        Payment payment = paymentRepository.findById(providerTransaction.get().getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "ProviderTransaction references a missing Payment: " + providerTransaction.get().getPaymentId()));

        if (payment.getState().isTerminal()) {
            return WebhookApplicationResult.ALREADY_TERMINAL;
        }

        PaymentState mapped = PROVIDER_STATUS_MAPPING.get(status);
        if (mapped != null && mapped != payment.getState()) {
            applyStateChange(payment, providerTransaction.get(), mapped, status.name());
        }
        return WebhookApplicationResult.APPLIED;
    }

    /** Dev/test-only entry point (see DevPaymentSimulationController) mirroring what a real webhook would do. */
    @Transactional
    public Payment simulateProviderConfirmation(UUID paymentId, boolean confirm) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new NotFoundException("Payment not found");
        }
        ProviderTransaction providerTransaction = providerTransactionRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment has no provider transaction: " + paymentId));

        var mockStatus = confirm ? ProviderPaymentStatus.CONFIRMED : ProviderPaymentStatus.REJECTED;
        if (openBankingProvider instanceof com.scansettle.api.openbanking.MockOpenBankingProvider mock) {
            mock.simulateProviderUpdate(providerTransaction.getProviderReference(), mockStatus);
        } else {
            throw new ApplicationException(HttpStatus.BAD_REQUEST, "not-a-mock-provider",
                    "Simulation is only available when the mock Open Banking provider is active.");
        }

        return syncAndGetStatus(paymentId);
    }

    private void applyStateChange(Payment payment, ProviderTransaction providerTransaction, PaymentState newState,
                                   String rawStatus) {
        PaymentState previousState = payment.getState();

        // A provider status update reports where a payment ended up, not every
        // intermediate hop (e.g. it may confirm without a separately observed
        // "submitted" event) — walk each legal hop rather than attempt an illegal
        // direct jump (docs/payment-states.md).
        for (PaymentState hop : previousState.pathTo(newState)) {
            payment.transitionTo(hop);
        }
        paymentRepository.save(payment);
        providerTransaction.updateRawStatus(rawStatus);
        providerTransactionRepository.save(providerTransaction);

        auditService.record(payment.getMerchantId(), AuditEvent.ActorType.SYSTEM, "openbanking-status-sync",
                "PAYMENT_STATE_CHANGED", "Payment", payment.getId().toString(),
                Map.of("from", previousState.name()), Map.of("to", newState.name()));

        if (newState == PaymentState.PAYMENT_CONFIRMED) {
            recordFee(payment);
        }
        if (RECONCILIABLE_STATES.contains(newState)) {
            recordReconciliation(payment, providerTransaction, newState);
        }
        if (newState.isTerminal()) {
            outcomeListeners.forEach(listener -> listener.onPaymentReachedTerminalState(payment));
        }
    }

    private void recordReconciliation(Payment payment, ProviderTransaction providerTransaction, PaymentState outcome) {
        Long confirmedAmount = outcome == PaymentState.PAYMENT_CONFIRMED ? payment.getAmountMinorUnits() : null;
        String note = outcome == PaymentState.PAYMENT_CONFIRMED
                ? null
                : "Payment did not settle (" + outcome + ") — nothing to reconcile against.";
        reconciliationRecordRepository.save(new ReconciliationRecord(UUID.randomUUID(), payment.getId(),
                providerTransaction.getId(), payment.getAmountMinorUnits(), confirmedAmount, note));
    }

    private void recordFee(Payment payment) {
        Merchant merchant = merchantRepository.findById(payment.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("Merchant not found: " + payment.getMerchantId()));
        var plan = pricingPlanRepository.findById(merchant.getPricingPlanId())
                .orElseThrow(() -> new IllegalStateException("Pricing plan not found: " + merchant.getPricingPlanId()));

        long feeMinorUnits = feeCalculator.calculateFeeMinorUnits(payment.getAmountMinorUnits(), plan);
        feeLedgerEntryRepository.save(new FeeLedgerEntry(
                UUID.randomUUID(), payment.getId(), payment.getMerchantId(), plan.getId(), feeMinorUnits));
    }
}
