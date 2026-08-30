package com.scansettle.api.merchant;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * docs/security.md Section 3 (tenant isolation) and the RBAC matrix — both are
 * structural correctness properties, not conventions, so they get their own
 * dedicated test rather than being incidentally covered elsewhere.
 */
class TenantIsolationAndRbacIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void merchantCannotReadAnotherMerchantsPaymentLink() throws Exception {
        var merchantA = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        var merchantB = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        String linkResponse = mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", merchantA.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 1000, "currencyCode", "GBP",
                                "description", "A's link", "reference", "A-1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkResponse).get("id").asText();

        // Merchant B must get 404, not merchant A's data and not a filtered empty
        // list that leaks the link's existence.
        mockMvc.perform(get("/api/v1/payment-links/" + linkId).header("Authorization", merchantB.authHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void merchantCannotReadAnotherMerchantsProfileOrAuditTrail() throws Exception {
        var merchantA = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        var merchantB = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        String profileA = mockMvc.perform(get("/api/v1/merchant/profile").header("Authorization", merchantA.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String profileB = mockMvc.perform(get("/api/v1/merchant/profile").header("Authorization", merchantB.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String tradingNameA = objectMapper.readTree(profileA).get("tradingName").asText();
        String tradingNameB = objectMapper.readTree(profileB).get("tradingName").asText();
        org.assertj.core.api.Assertions.assertThat(tradingNameA).isNotEqualTo(tradingNameB);

        // B's audit trail must never contain A's merchant id.
        String auditB = mockMvc.perform(get("/api/v1/audit-events").header("Authorization", merchantB.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(auditB).doesNotContain(merchantA.merchantId);
    }

    @Test
    void readOnlyRoleCannotCreatePaymentLink() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        String readOnlyToken = createUserWithRole(owner, "READ_ONLY");

        mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 1000, "currencyCode", "GBP",
                                "description", "x", "reference", "x"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readOnlyRoleCanStillViewTransactions() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        String readOnlyToken = createUserWithRole(owner, "READ_ONLY");

        mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + readOnlyToken))
                .andExpect(status().isOk());
    }

    @Test
    void staffRoleCannotManageBankAccount() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        String staffToken = createUserWithRole(owner, "STAFF");

        mockMvc.perform(put("/api/v1/merchant/bank-account")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sortCode", "123456", "accountNumber", "12345678", "accountName", "Test Ltd",
                                "currentPassword", "irrelevant-role-check-happens-first"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffRoleCanCreatePaymentLinks() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        String staffToken = createUserWithRole(owner, "STAFF");

        mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 1000, "currencyCode", "GBP",
                                "description", "x", "reference", "x"))))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerCanSetBankAccount_andItIsMaskedInResponses() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        mockMvc.perform(put("/api/v1/merchant/bank-account")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sortCode", "601613", "accountNumber", "12345678", "accountName", "Dave's Heating",
                                "currentPassword", owner.password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****5678"));
    }

    /** Creates a merchant user with the given role and logs them in, returning their access token. */
    private String createUserWithRole(MerchantTestFixture owner, String role) throws Exception {
        String email = "staffer-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        String password = "another-strong-password-1";

        mockMvc.perform(post("/api/v1/merchant-users")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "role", role, "temporaryPassword", password))))
                .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }
}
