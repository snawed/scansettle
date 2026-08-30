package com.scansettle.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Rate limiting is disabled under the default test profile (application-test.yml —
 * every other IT fires many legitimate rapid requests from one loopback address),
 * so this class deliberately re-enables it with a low, fast-to-exhaust bucket to
 * prove the mechanism itself works, in its own isolated Spring context.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.capacity=5",
        "app.rate-limit.refill-per-second=0.001"
})
class RateLimitFilterIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exceedingTheBucketOnAPublicEndpointReturns429() throws Exception {
        // /api/v1/merchants is both a genuinely public endpoint and rate-limited —
        // a fresh, valid registration body each time so a validation failure can't
        // be mistaken for the rate limit.
        int sawTooManyRequests = 0;
        for (int i = 0; i < 8; i++) {
            String unique = UUID.randomUUID().toString().substring(0, 8);
            Map<String, Object> body = Map.of(
                    "legalName", "Rate Test " + unique + " Ltd", "tradingName", "Rate Test " + unique,
                    "businessType", "Plumbing", "email", "rate-" + unique + "@example.test",
                    "password", "correct-horse-battery-staple");

            int status = mockMvc.perform(post("/api/v1/merchants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andReturn().getResponse().getStatus();

            if (status == 429) {
                sawTooManyRequests++;
            } else {
                assertThat(status).isEqualTo(201);
            }
        }

        // Capacity 5 against 8 attempts — at least the tail must have been throttled.
        assertThat(sawTooManyRequests).isGreaterThan(0);
    }
}
