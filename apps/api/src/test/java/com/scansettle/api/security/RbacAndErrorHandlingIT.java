package com.scansettle.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the Phase 2 authentication/RBAC foundation end-to-end using the dev-only
 * token endpoint (removed in Phase 3 — see DevTokenController javadoc) and confirms
 * every error path renders RFC 7807 Problem Details (docs/api.md, docs/security.md).
 */
class RbacAndErrorHandlingIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedUserCanSeeWhoAmI() throws Exception {
        String token = issueToken(Role.STAFF, "merchant-1");

        mockMvc.perform(get("/api/v1/dev/whoami").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.merchantId").value("merchant-1"));
    }

    @Test
    void adminRoleIsAllowedThroughPreAuthorize() throws Exception {
        String token = issueToken(Role.ADMIN, "merchant-1");

        mockMvc.perform(get("/api/v1/dev/admin-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminRoleIsRejectedWithProblemDetails_403() throws Exception {
        String token = issueToken(Role.STAFF, "merchant-1");

        mockMvc.perform(get("/api/v1/dev/admin-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void missingTokenIsRejectedWithProblemDetails_401() throws Exception {
        // Regression guard: the correlation-id filter must run before Spring
        // Security's chain, or an auth failure's error body has no real
        // correlationId (previously silently fell back to "unknown").
        mockMvc.perform(get("/api/v1/dev/whoami").header("X-Correlation-Id", "test-correlation-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-401"));
    }

    @Test
    void responsesCarryTheCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/open-banking/banks").header("X-Correlation-Id", "test-correlation-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-123"));
    }

    private String issueToken(Role role, String merchantId) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("role", role.name());
            put("merchantId", merchantId);
        }});

        String response = mockMvc.perform(post("/api/v1/dev/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
