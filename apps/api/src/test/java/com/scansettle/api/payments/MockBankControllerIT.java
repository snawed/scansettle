package com.scansettle.api.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full customer journey through ScanSettle's own mock-bank page — proves the
 * redirect -> approve -> real webhook -> confirmed chain works exactly as the actual
 * frontend pages drive it, not just the individual pieces in isolation.
 */
class MockBankControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void approvingAtTheMockBankConfirmsThePaymentViaARealWebhook() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 12000, "currencyCode", "GBP",
                                "description", "Mock bank flow", "reference", "MB-1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkResponse).get("id").asText();

        String startResponse = mockMvc.perform(post("/api/v1/payment-links/" + linkId + "/payments?bankId=monzo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode startJson = objectMapper.readTree(startResponse);
        String paymentId = startJson.get("paymentId").asText();
        String redirectUrl = startJson.get("redirectUrl").asText();
        String providerReference = redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1);

        // The mock-bank page's own info call — proves it can show the right amount/merchant.
        mockMvc.perform(get("/api/v1/mock-bank/" + providerReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinorUnits").value(12000))
                .andExpect(jsonPath("$.merchantTradingName").value(merchant.merchantTradingName));

        // Customer clicks "Approve" — this fires a real signed webhook internally.
        mockMvc.perform(post("/api/v1/mock-bank/" + providerReference + "/decision?approve=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED"));
    }

    @Test
    void decliningAtTheMockBankRejectsThePayment() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 3000, "currencyCode", "GBP",
                                "description", "Decline flow", "reference", "MB-2"))))
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

        mockMvc.perform(post("/api/v1/mock-bank/" + providerReference + "/decision?approve=false"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"));
    }
}
