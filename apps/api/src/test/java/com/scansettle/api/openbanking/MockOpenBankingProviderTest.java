package com.scansettle.api.openbanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.openbanking.model.AuthorisationResult;
import com.scansettle.api.openbanking.model.PaymentInstruction;
import com.scansettle.api.openbanking.model.PaymentStatusResult;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import com.scansettle.api.openbanking.model.RefundResult;
import com.scansettle.api.openbanking.model.WebhookRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockOpenBankingProviderTest {

    private static final String WEBHOOK_SECRET = "unit-test-webhook-secret";

    private final MockOpenBankingProvider provider =
            new MockOpenBankingProvider(new ObjectMapper().findAndRegisterModules(), WEBHOOK_SECRET,
                    "http://localhost:3000/mock-bank");

    @Test
    void createAuthorisationStartsInPendingState() {
        PaymentInstruction instruction = new PaymentInstruction("payment-1", 250000, "GBP",
                "Boiler Installation", "https://scansettle.test/return", null);

        AuthorisationResult authorisation = provider.createAuthorisation(instruction);

        assertThat(authorisation.providerReference()).isNotBlank();
        assertThat(authorisation.redirectUrl()).contains(authorisation.providerReference());

        PaymentStatusResult status = provider.getPaymentStatus(authorisation.providerReference());
        assertThat(status.status()).isEqualTo(ProviderPaymentStatus.PENDING);
    }

    @Test
    void simulateProviderUpdateAdvancesStatusLikeAWebhookWould() {
        PaymentInstruction instruction = new PaymentInstruction("payment-2", 9000, "GBP",
                "Table 14", "https://scansettle.test/return", "monzo");
        AuthorisationResult authorisation = provider.createAuthorisation(instruction);

        provider.simulateProviderUpdate(authorisation.providerReference(), ProviderPaymentStatus.CONFIRMED);

        assertThat(provider.getPaymentStatus(authorisation.providerReference()).status())
                .isEqualTo(ProviderPaymentStatus.CONFIRMED);
    }

    @Test
    void unknownProviderReferenceIsRejected() {
        assertThatThrownBy(() -> provider.getPaymentStatus("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundIsReportedAsUnsupported_matchingRealOpenBankingRails() {
        RefundResult result = provider.refundPayment("mock-any", 1000);

        assertThat(result.supported()).isFalse();
    }

    @Test
    void getSupportedBanksReturnsAPopulatedList() {
        assertThat(provider.getSupportedBanks()).isNotEmpty();
    }

    @Test
    void redirectUrlPointsAtScanSettlesOwnMockBankPage() {
        var instruction = new PaymentInstruction("payment-3", 1000, "GBP", "Test", null, null);
        var authorisation = provider.createAuthorisation(instruction);

        assertThat(authorisation.redirectUrl())
                .isEqualTo("http://localhost:3000/mock-bank/" + authorisation.providerReference());
    }

    @Test
    void correctlySignedFreshWebhookIsAccepted() {
        var signed = provider.buildSignedWebhook("mock-ref-1", ProviderPaymentStatus.CONFIRMED);

        var result = provider.handleWebhook(
                new WebhookRequest(Map.of("X-Webhook-Signature", signed.signature()), signed.rawBody()));

        assertThat(result.signatureValid()).isTrue();
        assertThat(result.providerReference()).isEqualTo("mock-ref-1");
        assertThat(result.status()).isEqualTo(ProviderPaymentStatus.CONFIRMED);
    }

    @Test
    void wrongSignatureIsRejected() {
        var signed = provider.buildSignedWebhook("mock-ref-2", ProviderPaymentStatus.CONFIRMED);

        var result = provider.handleWebhook(
                new WebhookRequest(Map.of("X-Webhook-Signature", "not-the-real-signature"), signed.rawBody()));

        assertThat(result.signatureValid()).isFalse();
    }

    @Test
    void missingSignatureHeaderIsRejected() {
        var signed = provider.buildSignedWebhook("mock-ref-3", ProviderPaymentStatus.CONFIRMED);

        var result = provider.handleWebhook(new WebhookRequest(Map.of(), signed.rawBody()));

        assertThat(result.signatureValid()).isFalse();
    }

    @Test
    void tamperedBodyWithOriginalSignatureIsRejected() {
        var signed = provider.buildSignedWebhook("mock-ref-4", ProviderPaymentStatus.CONFIRMED);
        String tamperedBody = signed.rawBody().replace("CONFIRMED", "REJECTED");

        var result = provider.handleWebhook(
                new WebhookRequest(Map.of("X-Webhook-Signature", signed.signature()), tamperedBody));

        assertThat(result.signatureValid()).isFalse();
    }

    @Test
    void malformedBodyIsRejectedNotThrown() {
        var result = provider.handleWebhook(
                new WebhookRequest(Map.of("X-Webhook-Signature", "anything"), "not-json-at-all"));

        assertThat(result.signatureValid()).isFalse();
    }
}
