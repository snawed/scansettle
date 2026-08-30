package com.scansettle.api.common.error;

import com.scansettle.api.security.JwtService;
import com.scansettle.api.security.Role;
import com.scansettle.api.support.AbstractIntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises GlobalExceptionHandler's RFC 7807 mapping via a test-only controller —
 * no real domain module throws these yet (Payments/Tables land in later phases), but
 * the exception-handling foundation itself needs proof now.
 */
@Import(GlobalExceptionHandlerIT.TestController.class)
class GlobalExceptionHandlerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void applicationExceptionRendersAsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test/not-found").header("Authorization", "Bearer " + authenticatedToken()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://scansettle.com/problems/not-found"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void validationFailureRendersAsProblemDetailsWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .header("Authorization", "Bearer " + authenticatedToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://scansettle.com/problems/validation-failed"))
                .andExpect(jsonPath("$.errors[0]").exists());
    }

    private String authenticatedToken() {
        return jwtService.issue(new com.scansettle.api.security.AuthenticatedPrincipal(
                "test@scansettle.test", Role.ADMIN, "merchant-1"));
    }

    @RestController
    @Validated
    static class TestController {

        record ValidatedBody(@NotBlank String name) {
        }

        @GetMapping("/api/v1/test/not-found")
        String notFound() {
            throw new NotFoundException("Payment not found");
        }

        @PostMapping("/api/v1/test/validate")
        String validate(@RequestBody @Valid ValidatedBody body) {
            return "ok";
        }
    }
}
