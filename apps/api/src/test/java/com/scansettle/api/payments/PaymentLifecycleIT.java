package com.scansettle.api.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end "Create Payment" flow the Product Owner asked to pull fully into
 * Phase 3: merchant creates a link, a customer (no ScanSettle account) pays it via
 * the mock provider, the state machine advances, a fee is recorded, and it shows up
 * in the dashboard and transaction list.
 */
class PaymentLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MerchantTestFixture merchant;

    @BeforeEach
    void setUp() throws Exception {
        merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
    }

    @Test
    void fullLifecycle_createLink_customerPays_confirmedByProvider_feeRecorded() throws Exception {
        // 1. Merchant creates a payment link.
        Map<String, Object> createLinkBody = Map.of(
                "amountMinorUnits", 250000,
                "currencyCode", "GBP",
                "description", "Boiler Installation",
                "reference", "INV-1023");
        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLinkBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkResponse).get("id").asText();

        // 2. QR code is a real PNG.
        mockMvc.perform(get("/api/v1/payment-links/" + linkId + "/qr").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // 3. Anonymous customer views the public link — no auth header at all.
        mockMvc.perform(get("/api/v1/payment-links/" + linkId + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinorUnits").value(250000))
                .andExpect(jsonPath("$.payable").value(true));

        // 4. Customer starts a payment attempt — public, calls the mock provider.
        String startResponse = mockMvc.perform(post("/api/v1/payment-links/" + linkId + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REDIRECTED_TO_BANK"))
                .andExpect(jsonPath("$.redirectUrl").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode startJson = objectMapper.readTree(startResponse);
        String paymentId = startJson.get("paymentId").asText();
        // Points at ScanSettle's own mock-bank page (Phase 6), not an unresolvable fake domain.
        assertThat(startJson.get("redirectUrl").asText()).contains("/mock-bank/");

        // 5. Status poll before the bank has done anything — still pending, never
        //    trusted as confirmed just because the customer is "back" (docs/api.md).
        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminal").value(false));

        // 6. The bank confirms (simulated — real webhook arrives in Phase 4).
        mockMvc.perform(post("/api/v1/dev/payments/" + paymentId + "/simulate-provider-status?confirm=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED"));

        // 7. Status now reflects the provider-confirmed truth.
        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.terminal").value(true));

        // 8. Merchant sees it in the transaction list and detail view.
        mockMvc.perform(get("/api/v1/payments").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paymentId))
                .andExpect(jsonPath("$[0].state").value("PAYMENT_CONFIRMED"));

        mockMvc.perform(get("/api/v1/payments/" + paymentId).header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinorUnits").value(250000));

        // 9. Dashboard reflects the confirmed payment and its fee (0.35% of £2,500 = £8.75, capped at £2.00).
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayConfirmedAmountMinorUnits").value(250000))
                .andExpect(jsonPath("$.todayConfirmedCount").value(1))
                .andExpect(jsonPath("$.monthFeesMinorUnits").value(200));
    }

    @Test
    void rejectedPaymentDoesNotCountTowardConfirmedTotalsOrFees() throws Exception {
        Map<String, Object> createLinkBody = Map.of(
                "amountMinorUnits", 5000, "currencyCode", "GBP", "description", "Callout", "reference", "INV-2000");
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
        String paymentId = objectMapper.readTree(startResponse).get("paymentId").asText();

        mockMvc.perform(post("/api/v1/dev/payments/" + paymentId + "/simulate-provider-status?confirm=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"));

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayConfirmedCount").value(0))
                .andExpect(jsonPath("$.monthFeesMinorUnits").value(0));
    }

    @Test
    void expiredOrClosedLinkCannotBePaid() throws Exception {
        Map<String, Object> createLinkBody = Map.of(
                "amountMinorUnits", 1000, "currencyCode", "GBP", "description", "Test", "reference", "INV-9999",
                "expiresAt", "2000-01-01T00:00:00Z");
        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLinkBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/payment-links/" + linkId + "/payments"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://scansettle.com/problems/payment-link-not-payable"));
    }
}
