package com.scansettle.api.payments;

import com.scansettle.api.common.error.NotFoundException;
import com.scansettle.api.merchant.Merchant;
import com.scansettle.api.merchant.MerchantRepository;
import com.scansettle.api.openbanking.MockOpenBankingProvider;
import com.scansettle.api.openbanking.OpenBankingProvider;
import com.scansettle.api.openbanking.WebhookIngestionService;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ScanSettle's own stand-in for a real bank's authentication/consent screen — where
 * {@link MockOpenBankingProvider#createAuthorisation} sends the customer's browser
 * (docs/payment-states.md's REDIRECTED_TO_BANK). "Approve"/"Decline" here builds a
 * genuinely HMAC-signed webhook payload and runs it through the exact same
 * {@link WebhookIngestionService} the real public webhook endpoint uses — signature
 * verification, idempotency, and all — called directly rather than over a
 * self-HTTP loopback (fragile across environments for no extra real coverage, since
 * the signing and verification both happen in this same process either way).
 * dev/test profiles only: no real deployment ever needs a pretend bank.
 */
@RestController
@Profile({"dev", "test"})
public class MockBankController {

    private final OpenBankingProvider openBankingProvider;
    private final WebhookIngestionService webhookIngestionService;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;

    public MockBankController(OpenBankingProvider openBankingProvider,
                               WebhookIngestionService webhookIngestionService,
                               ProviderTransactionRepository providerTransactionRepository,
                               PaymentRepository paymentRepository, MerchantRepository merchantRepository) {
        this.openBankingProvider = openBankingProvider;
        this.webhookIngestionService = webhookIngestionService;
        this.providerTransactionRepository = providerTransactionRepository;
        this.paymentRepository = paymentRepository;
        this.merchantRepository = merchantRepository;
    }

    public record MockBankInfoResponse(long amountMinorUnits, String currencyCode, String merchantTradingName) {
    }

    @GetMapping("/api/v1/mock-bank/{providerReference}")
    public MockBankInfoResponse info(@PathVariable String providerReference) {
        Payment payment = paymentForReference(providerReference);
        String tradingName = merchantRepository.findById(payment.getMerchantId())
                .map(Merchant::getTradingName)
                .orElse("Unknown merchant");
        return new MockBankInfoResponse(payment.getAmountMinorUnits(), payment.getCurrencyCode(), tradingName);
    }

    public record DecisionResponse(boolean sent) {
    }

    @PostMapping("/api/v1/mock-bank/{providerReference}/decision")
    public DecisionResponse decide(@PathVariable String providerReference, @RequestParam boolean approve) {
        // Confirms the reference is real before "the bank" does anything with it.
        paymentForReference(providerReference);

        if (!(openBankingProvider instanceof MockOpenBankingProvider mock)) {
            throw new IllegalStateException("Mock bank is only available when the mock provider is active");
        }

        var status = approve ? ProviderPaymentStatus.CONFIRMED : ProviderPaymentStatus.REJECTED;
        var signedWebhook = mock.buildSignedWebhook(providerReference, status);

        webhookIngestionService.ingestOpenBankingWebhook(
                Map.of("X-Webhook-Signature", signedWebhook.signature()), signedWebhook.rawBody());

        return new DecisionResponse(true);
    }

    private Payment paymentForReference(String providerReference) {
        var providerTransaction = providerTransactionRepository.findByProviderReference(providerReference)
                .orElseThrow(() -> new NotFoundException("Unknown provider reference"));
        return paymentRepository.findById(providerTransaction.getPaymentId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
    }
}
