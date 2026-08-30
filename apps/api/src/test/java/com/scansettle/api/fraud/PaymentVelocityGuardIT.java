package com.scansettle.api.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The velocity guard is disabled under the default test profile for the same
 * reason rate limiting is (application-test.yml) — isolated context here with a
 * low per-IP threshold, and the per-merchant threshold set high enough to isolate
 * which of the two checks actually fired (ADR-0011).
 */
@TestPropertySource(properties = {
        "app.velocity-guard.enabled=true",
        "app.velocity-guard.per-ip-threshold=2",
        "app.velocity-guard.per-merchant-threshold=1000",
        "app.velocity-guard.window-seconds=300",
        "app.velocity-guard.cooldown-seconds=3600"
})
class PaymentVelocityGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void repeatedPaymentAttemptsFromOneSourceAutoRaiseAFraudFlag() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 1000, "currencyCode", "GBP",
                                "description", "Velocity test", "reference", "VEL-1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkResponse).get("id").asText();

        // Threshold is 2 in 5 minutes — the 3rd attempt from this IP must push past it.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/payment-links/" + linkId + "/payments"))
                    .andExpect(status().isOk());
        }

        String opsToken = opsLogin();
        mockMvc.perform(get("/api/v1/admin/fraud-flags?merchantId=" + merchant.merchantId)
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].raisedBy").value("SYSTEM"))
                .andExpect(jsonPath("$[0].reason").value(org.hamcrest.Matchers.containsString("velocity")));
    }

    private String opsLogin() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ops@scansettle.dev", "password", "OpsPassword123!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
