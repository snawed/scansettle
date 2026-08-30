package com.scansettle.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.openbanking.MockOpenBankingProvider;
import com.scansettle.api.openbanking.model.ProviderPaymentStatus;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8: a genuinely separate ScanSettle Ops/Support login (Role.PLATFORM_ADMIN,
 * never scoped to a merchant) with cross-merchant visibility — merchant list/suspend,
 * payment investigation, webhook inspection, fraud flags. Also proves the two token
 * types can never satisfy each other's endpoints.
 */
class AdminOpsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockOpenBankingProvider mockOpenBankingProvider;

    private MerchantTestFixture merchant;
    private String opsToken;

    @BeforeEach
    void setUp() throws Exception {
        merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        opsToken = opsLogin();
    }

    @Test
    void opsCanLogInAndSeeTheMerchantInTheAdminList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/merchants?q=" + merchant.merchantTradingName)
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradingName").value(merchant.merchantTradingName))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void aMerchantTokenCannotCallAdminEndpointsAndAnOpsTokenCannotCallMerchantEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/merchants").header("Authorization", merchant.authHeader()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendingAMerchantBlocksLoginAndReactivatingRestoresIt() throws Exception {
        mockMvc.perform(post("/api/v1/admin/merchants/" + merchant.merchantId + "/suspend")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", merchant.ownerEmail, "password", merchant.password))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://scansettle.com/problems/merchant-suspended"));

        mockMvc.perform(post("/api/v1/admin/merchants/" + merchant.merchantId + "/reactivate")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", merchant.ownerEmail, "password", merchant.password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void investigatingAPaymentReturnsTheProviderTransactionAndWebhookHistory() throws Exception {
        Map<String, Object> createLinkBody = Map.of(
                "amountMinorUnits", 5000, "currencyCode", "GBP", "description", "Investigate me", "reference", "INV-ADMIN");
        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLinkBody)))
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

        var signed = mockOpenBankingProvider.buildSignedWebhook(providerReference, ProviderPaymentStatus.CONFIRMED);
        mockMvc.perform(post("/api/v1/webhooks/open-banking")
                        .header("X-Webhook-Signature", signed.signature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.rawBody()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/payments/" + paymentId + "/investigate")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.id").value(paymentId))
                .andExpect(jsonPath("$.payment.state").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.providerTransaction.providerReference").value(providerReference))
                .andExpect(jsonPath("$.webhookEvents.length()").value(1))
                .andExpect(jsonPath("$.webhookEvents[0].processingResult").value("PROCESSED"))
                .andExpect(jsonPath("$.reconciliation.length()").value(1))
                .andExpect(jsonPath("$.reconciliation[0].matched").value(true));

        mockMvc.perform(get("/api/v1/admin/webhooks").header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerReference").value(providerReference));
    }

    @Test
    void fraudFlagsCanBeRaisedListedAndCleared() throws Exception {
        String raiseResponse = mockMvc.perform(post("/api/v1/admin/fraud-flags")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "merchantId", merchant.merchantId, "reason", "Unusually high volume overnight"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String flagId = objectMapper.readTree(raiseResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/fraud-flags?merchantId=" + merchant.merchantId)
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(flagId));

        mockMvc.perform(post("/api/v1/admin/fraud-flags/" + flagId + "/clear")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"));
    }

    @Test
    void raisingAFraudFlagWithBothOrNeitherTargetIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/fraud-flags")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "No target at all"))))
                .andExpect(status().isBadRequest());
    }

    private String opsLogin() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ops@scansettle.dev", "password", "OpsPassword123!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
