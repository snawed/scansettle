package com.scansettle.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 hardening: bank-account step-up re-authentication and ops/PLATFORM_ADMIN
 * MFA — both new security-critical behaviour, not just refactors of existing tested
 * paths, so they get dedicated coverage (ADR-0011).
 */
class SecurityHardeningIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bankAccountChangeIsRejectedWithTheWrongCurrentPassword() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        mockMvc.perform(put("/api/v1/merchant/bank-account")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sortCode", "601613", "accountNumber", "12345678", "accountName", "Dave's Heating",
                                "currentPassword", "definitely-the-wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://scansettle.com/problems/step-up-failed"));
    }

    @Test
    void bankAccountChangeRequiresAFreshMfaCodeOnceMfaIsEnabled() throws Exception {
        var owner = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        String enrollResponse = mockMvc.perform(post("/api/v1/auth/mfa/enroll").header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(enrollResponse).get("secret").asText();

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", currentTotpCode(secret)))))
                .andExpect(status().isOk());

        // Correct password, but no MFA code supplied — must be rejected, not silently allowed.
        mockMvc.perform(put("/api/v1/merchant/bank-account")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sortCode", "601613", "accountNumber", "12345678", "accountName", "Dave's Heating",
                                "currentPassword", owner.password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://scansettle.com/problems/step-up-failed"));

        // Correct password AND a fresh code — succeeds.
        mockMvc.perform(put("/api/v1/merchant/bank-account")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sortCode", "601613", "accountNumber", "12345678", "accountName", "Dave's Heating",
                                "currentPassword", owner.password, "mfaCode", currentTotpCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****5678"));
    }

    @Test
    void platformAdminCanEnrollMfaAndMustThenUseItToLogIn() throws Exception {
        String accessToken = opsLogin();

        String enrollResponse = mockMvc.perform(post("/api/v1/admin/auth/mfa/enroll")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(enrollResponse).get("secret").asText();

        mockMvc.perform(post("/api/v1/admin/auth/mfa/verify")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", currentTotpCode(secret)))))
                .andExpect(status().isOk());

        // A plain login now returns an MFA challenge, not a token.
        String loginResponse = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ops@scansettle.dev", "password", "OpsPassword123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.mfaChallengeToken").exists())
                .andReturn().getResponse().getContentAsString();
        String mfaChallengeToken = objectMapper.readTree(loginResponse).get("mfaChallengeToken").asText();

        mockMvc.perform(post("/api/v1/admin/auth/mfa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mfaChallengeToken", mfaChallengeToken, "code", currentTotpCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
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

    /** Same RFC 6238 math as TotpService, kept independent here on purpose — this
     *  test should fail if the production algorithm and the test's expectation of
     *  "what a real authenticator app would compute" ever diverge. */
    private String currentTotpCode(String base32Secret) throws Exception {
        var totpService = new TotpService();
        var verifyMethod = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        verifyMethod.setAccessible(true);
        long currentStep = java.time.Instant.now().getEpochSecond() / 30;
        return (String) verifyMethod.invoke(totpService, base32Secret, currentStep);
    }
}
