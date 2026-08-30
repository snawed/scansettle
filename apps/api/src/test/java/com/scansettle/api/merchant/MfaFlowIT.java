package com.scansettle.api.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.security.TotpService;
import com.scansettle.api.support.AbstractIntegrationTest;
import com.scansettle.api.support.MerchantTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MfaFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TotpService totpService;

    @Test
    void enrollThenLoginRequiresTotpCode() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();

        // Enroll — get a secret back.
        String enrollResponse = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(enrollResponse).get("secret").asText();

        // Confirm enrollment with a real TOTP code for that secret.
        String code = currentCodeFor(secret);
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());

        // From now on, a plain login must NOT return a usable access token —
        // it must demand the second factor.
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", merchant.ownerEmail, "password", merchant.password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.mfaChallengeToken").exists())
                .andReturn().getResponse().getContentAsString();
        String mfaChallengeToken = objectMapper.readTree(loginResponse).get("mfaChallengeToken").asText();

        // Wrong code is rejected.
        mockMvc.perform(post("/api/v1/auth/mfa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mfaChallengeToken", mfaChallengeToken, "code", "000000"))))
                .andExpect(status().isUnauthorized());

        // Correct code completes login.
        mockMvc.perform(post("/api/v1/auth/mfa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mfaChallengeToken", mfaChallengeToken, "code", currentCodeFor(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void mfaChallengeTokenCannotBeUsedAsABearerToken() throws Exception {
        var merchant = new MerchantTestFixture(mockMvc, objectMapper).registerAndLogin();
        String enrollResponse = mockMvc.perform(post("/api/v1/auth/mfa/enroll")
                        .header("Authorization", merchant.authHeader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(enrollResponse).get("secret").asText();
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .header("Authorization", merchant.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", currentCodeFor(secret)))))
                .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", merchant.ownerEmail, "password", merchant.password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String mfaChallengeToken = objectMapper.readTree(loginResponse).get("mfaChallengeToken").asText();

        // A partial-auth token must not work as a real bearer token anywhere —
        // JwtService.validate() rejects a type=mfa_challenge token outright.
        mockMvc.perform(post("/api/v1/payment-links")
                        .header("Authorization", "Bearer " + mfaChallengeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountMinorUnits", 1000, "currencyCode", "GBP", "description", "x", "reference", "x"))))
                .andExpect(status().isUnauthorized());
    }

    private String currentCodeFor(String secret) throws Exception {
        var method = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        method.setAccessible(true);
        long currentStep = System.currentTimeMillis() / 1000 / 30;
        return (String) method.invoke(totpService, secret, currentStep);
    }
}
