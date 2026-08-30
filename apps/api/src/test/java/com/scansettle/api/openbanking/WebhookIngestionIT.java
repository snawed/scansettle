package com.scansettle.api.openbanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Phase 4 webhook path end-to-end over actual HTTP — signature
 * verification, idempotent replay handling, staleness rejection — replacing what
 * Phase 3's dev simulate-endpoint shortcut couldn't prove.
 */
class WebhookIngestionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockOpenBankingProvider mockOpenBankingProvider;

    private record StartedPayment(String paymentId, String providerReference) {
    }

    private StartedPayment createLinkAndStartPayment(MerchantTestFixture merchant) throws Exception {
        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 5000, "currencyCode", "GBP",
                                "description", "Webhook test", "reference", "WH-1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkResponse).get("id").asText();

        String startResponse = mockMvc.perform(post("/api/v1/payment-links/" + linkId + "/payments"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode startJson = objectMapper.readTree(startResponse);
        String paymentId = startJson.get("paymentId").asText();
        String redirectUrl = startJson.get("redirectUrl").asText();
        String providerReference = redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1);

        return new StartedPayment(paymentId, providerReference);
    }

    @Test
    void correctlySignedWebhookConfirmsThePayment() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        var started = createLinkAndStartPayment(merchant);

        var signed = mockOpenBankingProvider.buildSignedWebhook(started.providerReference(), ProviderPaymentStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payments/" + started.paymentId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED"));

        // Reconciliation foundation: a matched record exists for this payment.
        String reconciliation = mockMvc.perform(get("/api/v1/reconciliation").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(reconciliation).contains(started.paymentId()).contains("\"matched\":true");
    }

    @Test
    void wrongSignatureIsRejectedAndPaymentStaysUnconfirmed() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        var started = createLinkAndStartPayment(merchant);

        var signed = mockOpenBankingProvider.buildSignedWebhook(started.providerReference(), ProviderPaymentStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", "forged-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/" + started.paymentId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REDIRECTED_TO_BANK"));
    }

    @Test
    void duplicateWebhookDeliveryIsNotReprocessed() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        var started = createLinkAndStartPayment(merchant);

        var signed = mockOpenBankingProvider.buildSignedWebhook(started.providerReference(), ProviderPaymentStatus.CONFIRMED);

        // First delivery confirms the payment.
        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());

        // Exact same delivery again (same eventId/body/signature) — acknowledged, not reprocessed.
        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());

        // Only one fee ledger entry / dashboard count should exist — not double-counted.
        String summary = mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(summary).get("todayConfirmedCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void staleWebhookTimestampIsRejected() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        var started = createLinkAndStartPayment(merchant);

        // Hand-build a payload with a timestamp well outside the freshness window,
        // signed correctly, to prove staleness alone is enough to reject it.
        String staleBody = objectMapper.writeValueAsString(Map.of(
                "eventId", "stale-event-1",
                "providerReference", started.providerReference(),
                "status", "CONFIRMED",
                "timestamp", Instant.now().minusSeconds(3600).toString()));
        String signature = signRawBody(staleBody);

        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/" + started.paymentId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REDIRECTED_TO_BANK"));
    }

    private String signRawBody(String rawBody) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                "test-only-mock-webhook-signing-secret-do-not-use-in-prod".getBytes(), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes()));
    }
}
