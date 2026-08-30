package com.scansettle.api.admin;

import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.merchant.Merchant;
import com.scansettle.api.merchant.MerchantRepository;
import com.scansettle.api.payments.Payment;
import com.scansettle.api.payments.PaymentRepository;
import com.scansettle.api.payments.ProviderTransaction;
import com.scansettle.api.payments.ProviderTransactionRepository;
import com.scansettle.api.openbanking.WebhookEvent;
import com.scansettle.api.openbanking.WebhookEventRepository;
import com.scansettle.api.reconciliation.ReconciliationRecord;
import com.scansettle.api.reconciliation.ReconciliationRecordRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Investigate a payment stuck in PAYMENT_SUBMITTED for over 15 minutes" — the exact
 * use case docs/architecture.md calls out for ScanSettle Ops. Pulls together the
 * Payment, its ProviderTransaction, every WebhookEvent that named its provider
 * reference, and its ReconciliationRecord in one call rather than four.
 */
@RestController
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;

    public AdminPaymentController(PaymentRepository paymentRepository, MerchantRepository merchantRepository,
                                   ProviderTransactionRepository providerTransactionRepository,
                                   WebhookEventRepository webhookEventRepository,
                                   ReconciliationRecordRepository reconciliationRecordRepository) {
        this.paymentRepository = paymentRepository;
        this.merchantRepository = merchantRepository;
        this.providerTransactionRepository = providerTransactionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
    }

    public record PaymentSummary(String id, String merchantId, String merchantTradingName, long amountMinorUnits,
                                  String currencyCode, String state, Instant createdAt, Instant updatedAt) {
    }

    public record ProviderTransactionSummary(String id, String provider, String providerReference, String rawStatus,
                                              Instant lastSyncedAt) {
    }

    public record WebhookEventSummary(String id, String source, boolean signatureValid, String processingResult,
                                       Instant receivedAt, Instant processedAt) {
    }

    public record ReconciliationSummary(String id, long expectedAmountMinorUnits, Long confirmedAmountMinorUnits,
                                         boolean matched, String discrepancyNote, Instant createdAt) {
    }

    public record InvestigateResponse(PaymentSummary payment, ProviderTransactionSummary providerTransaction,
                                       List<WebhookEventSummary> webhookEvents,
                                       List<ReconciliationSummary> reconciliation) {
    }

    @GetMapping("/api/v1/admin/payments/{id}/investigate")
    public InvestigateResponse investigate(@PathVariable UUID id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new NotFoundException("Payment not found"));
        Merchant merchant = merchantRepository.findById(payment.getMerchantId())
                .orElseThrow(() -> new IllegalStateException("Payment references a missing Merchant"));

        ProviderTransaction providerTransaction = providerTransactionRepository.findByPaymentId(id).orElse(null);

        List<WebhookEventSummary> webhookEvents = providerTransaction == null ? List.of()
                : webhookEventRepository.findByProviderReferenceOrderByReceivedAtDesc(providerTransaction.getProviderReference())
                        .stream().map(this::toWebhookSummary).toList();

        List<ReconciliationSummary> reconciliation = reconciliationRecordRepository.findByPaymentId(id).stream()
                .map(this::toReconciliationSummary).toList();

        return new InvestigateResponse(
                new PaymentSummary(payment.getId().toString(), payment.getMerchantId().toString(),
                        merchant.getTradingName(), payment.getAmountMinorUnits(), payment.getCurrencyCode(),
                        payment.getState().name(), payment.getCreatedAt(), payment.getUpdatedAt()),
                providerTransaction == null ? null : new ProviderTransactionSummary(
                        providerTransaction.getId().toString(), providerTransaction.getProvider(),
                        providerTransaction.getProviderReference(), providerTransaction.getRawStatus(),
                        providerTransaction.getLastSyncedAt()),
                webhookEvents, reconciliation);
    }

    private WebhookEventSummary toWebhookSummary(WebhookEvent event) {
        return new WebhookEventSummary(event.getId().toString(), event.getSource().name(), event.isSignatureValid(),
                event.getProcessingResult() != null ? event.getProcessingResult().name() : null,
                event.getReceivedAt(), event.getProcessedAt());
    }

    private ReconciliationSummary toReconciliationSummary(ReconciliationRecord record) {
        return new ReconciliationSummary(record.getId().toString(), record.getExpectedAmountMinorUnits(),
                record.getConfirmedAmountMinorUnits(), record.isMatched(), record.getDiscrepancyNote(),
                record.getCreatedAt());
    }
}
