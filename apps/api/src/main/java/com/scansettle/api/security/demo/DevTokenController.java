package com.scansettle.api.security.demo;

import com.scansettle.api.security.AuthenticatedPrincipal;
import com.scansettle.api.security.JwtService;
import com.scansettle.api.security.Role;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2 scaffolding only: issues a token for a chosen role/merchant so the RBAC
 * plumbing (JwtAuthenticationFilter, method security, {@code @PreAuthorize}) can be
 * exercised end-to-end before real merchant registration/login exists.
 *
 * <p><b>Removed in Phase 3</b>, replaced by real merchant login backed by a persisted
 * {@code MerchantUser} (password hash, MFA, OIDC where applicable) — see
 * docs/architecture.md Phase 3 scope. Active only in the {@code dev} and {@code test}
 * profiles so it can never run in a production deployment.
 */
@RestController
@Profile({"dev", "test"})
public class DevTokenController {

    private final JwtService jwtService;

    public DevTokenController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public record TokenRequest(@NotNull Role role, String merchantId) {
    }

    public record TokenResponse(String accessToken) {
    }

    @PostMapping("/api/v1/dev/token")
    public TokenResponse issueToken(@RequestBody TokenRequest request) {
        String merchantId = request.merchantId() != null ? request.merchantId() : "demo-merchant";
        var principal = new AuthenticatedPrincipal("dev-user@scansettle.test", request.role(), merchantId);
        return new TokenResponse(jwtService.issue(principal));
    }
}
