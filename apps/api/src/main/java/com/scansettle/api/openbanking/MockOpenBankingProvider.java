package com.scansettle.api.openbanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.openbanking.model.AuthorisationResult;
import com.scansettle.api.openbanking.model.PaymentInstruction;
import com.scansettle.api.openbanking.model.PaymentStatusResult;
import com.scansettle.api.openbanking.model.ProviderPayment;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import com.scansettle.api.openbanking.model.RefundResult;
import com.scansettle.api.openbanking.model.SupportedBank;
import com.scansettle.api.openbanking.model.WebhookProcessingResult;
import com.scansettle.api.openbanking.model.WebhookRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fully working fake so the entire ScanSettle payment journey — and its failure
 * modes — can be built and demonstrated before any real regulated provider is
 * selected (docs/open-banking.md). Payments start {@code PENDING}; the redirect URL
 * points at ScanSettle's own {@code /mock-bank} page (docs/payment-states.md's
 * customer journey needs somewhere real to "authenticate", not an unresolvable
 * fake domain), whose decision endpoint fires a genuinely HMAC-signed webhook back
 * at {@code POST /api/v1/webhooks/open-banking} — the same validation path a real
 * provider's webhook would go through.
 *
 * <p>State is in-memory only — this is a development/test double, never wired in a
 * production profile (see {@link OpenBankingConfig}).
 */
public class MockOpenBankingProvider implements OpenBankingProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration WEBHOOK_FRESHNESS_WINDOW = Duration.ofMinutes(5);

    private static final List<SupportedBank> SUPPORTED_BANKS = List.of(
            new SupportedBank("monzo", "Monzo", null),
            new SupportedBank("barclays", "Barclays", null),
            new SupportedBank("hsbc-uk", "HSBC UK", null),
            new SupportedBank("natwest", "NatWest", null),
            new SupportedBank("lloyds", "Lloyds Bank", null),
            new SupportedBank("nationwide", "Nationwide", null),
            new SupportedBank("santander-uk", "Santander UK", null),
            new SupportedBank("starling", "Starling Bank", null),
            new SupportedBank("tsb", "TSB", null)
    );

    private final Map<String, ProviderPaymentStatus> paymentsByReference = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final String mockBankBaseUrl;

    public MockOpenBankingProvider(ObjectMapper objectMapper, String webhookSecret, String mockBankBaseUrl) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
        this.mockBankBaseUrl = mockBankBaseUrl;
    }

    @Override
    public AuthorisationResult createAuthorisation(PaymentInstruction instruction) {
        String providerReference = newProviderReference();
        paymentsByReference.put(providerReference, ProviderPaymentStatus.PENDING);
        return new AuthorisationResult(providerReference, buildRedirectUrl(providerReference));
    }

    @Override
    public ProviderPayment createPayment(PaymentInstruction instruction) {
        String providerReference = newProviderReference();
        paymentsByReference.put(providerReference, ProviderPaymentStatus.PENDING);
        return new ProviderPayment(providerReference, instruction.merchantReference(),
                ProviderPaymentStatus.PENDING, buildRedirectUrl(providerReference));
    }

    @Override
    public PaymentStatusResult getPaymentStatus(String providerReference) {
        ProviderPaymentStatus status = paymentsByReference.get(providerReference);
        if (status == null) {
            throw new IllegalArgumentException("Unknown provider reference: " + providerReference);
        }
        return new PaymentStatusResult(providerReference, status, Instant.now(), null);
    }

    @Override
    public void cancelPayment(String providerReference) {
        paymentsByReference.computeIfPresent(providerReference, (ref, status) -> ProviderPaymentStatus.CANCELLED);
    }

    @Override
    public RefundResult refundPayment(String providerReference, long amountMinorUnits) {
        // The mock deliberately mirrors real Open Banking rails (ADR-0004): push
        // payments have no native reversal, so this always reports unsupported.
        return RefundResult.unsupported();
    }

    /** JSON body shape signed/sent by {@link #buildSignedWebhook} and parsed here. */
    private record WebhookPayload(String eventId, String providerReference, String status, Instant timestamp) {
    }

    @Override
    public WebhookProcessingResult handleWebhook(WebhookRequest request) {
        String signatureHeader = request.headers().get("X-Webhook-Signature");
        boolean signatureValid = signatureHeader != null && signatureHeader.equals(sign(request.rawBody()));

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(request.rawBody(), WebhookPayload.class);
        } catch (Exception e) {
            return new WebhookProcessingResult(false, null, null, null);
        }

        boolean fresh = payload.timestamp() != null
                && Duration.between(payload.timestamp(), Instant.now()).abs().compareTo(WEBHOOK_FRESHNESS_WINDOW) <= 0;

        return new WebhookProcessingResult(
                signatureValid && fresh,
                payload.providerReference(),
                signatureValid && fresh ? ProviderPaymentStatus.valueOf(payload.status()) : null,
                payload.eventId());
    }

    @Override
    public List<SupportedBank> getSupportedBanks() {
        return SUPPORTED_BANKS;
    }

    public record SignedWebhook(String rawBody, String signature) {
    }

    /**
     * Builds the exact payload+signature a real provider's webhook call would send.
     * Used by the mock-bank decision endpoint to fire a genuine, verifiable webhook
     * back at ScanSettle rather than mutating Payment state directly.
     */
    public SignedWebhook buildSignedWebhook(String providerReference, ProviderPaymentStatus status) {
        try {
            String rawBody = objectMapper.writeValueAsString(
                    new WebhookPayload(UUID.randomUUID().toString(), providerReference, status.name(), Instant.now()));
            return new SignedWebhook(rawBody, sign(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build mock webhook payload", e);
        }
    }

    /** Test/demo-only: advance the mock's own record of a payment's status directly (no webhook round-trip). */
    public void simulateProviderUpdate(String providerReference, ProviderPaymentStatus newStatus) {
        if (!paymentsByReference.containsKey(providerReference)) {
            throw new IllegalArgumentException("Unknown provider reference: " + providerReference);
        }
        paymentsByReference.put(providerReference, newStatus);
    }

    private String buildRedirectUrl(String providerReference) {
        return mockBankBaseUrl + "/" + providerReference;
    }

    private String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }

    private String newProviderReference() {
        return "mock-" + UUID.randomUUID();
    }
}
