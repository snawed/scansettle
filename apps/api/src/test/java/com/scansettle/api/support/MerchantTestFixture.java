package com.scansettle.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registers a fresh merchant + OWNER user and logs in, for tests that need a real
 *  authenticated merchant context rather than the dev-token shortcut. */
public class MerchantTestFixture {

    public final String merchantTradingName;
    public final String ownerEmail;
    public final String password = "correct-horse-battery-staple";
    public String merchantId;
    public String accessToken;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public MerchantTestFixture(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        String unique = UUID.randomUUID().toString().substring(0, 8);
        this.merchantTradingName = "Dave's Heating " + unique;
        this.ownerEmail = "owner-" + unique + "@example.test";
    }

    public MerchantTestFixture registerAndLogin() throws Exception {
        Map<String, Object> registerBody = Map.of(
                "legalName", merchantTradingName + " Ltd",
                "tradingName", merchantTradingName,
                "businessType", "Plumbing",
                "email", ownerEmail,
                "password", password);

        String registerResponse = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode registerJson = objectMapper.readTree(registerResponse);
        this.merchantId = registerJson.get("merchantId").asText();

        Map<String, Object> loginBody = Map.of("email", ownerEmail, "password", password);
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        this.accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        return this;
    }

    public String authHeader() {
        return "Bearer " + accessToken;
    }
}
