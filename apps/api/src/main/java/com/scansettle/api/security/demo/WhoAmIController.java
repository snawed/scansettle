package com.scansettle.api.security.demo;

import com.scansettle.api.security.AuthenticatedPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Demonstrates that authentication and RBAC enforcement work end-to-end:
 * {@code /whoami} requires any authenticated principal, {@code /admin-only} requires
 * the ADMIN role via {@code @PreAuthorize}. Superseded by real merchant-scoped
 * endpoints from Phase 3 onward; kept as a minimal, always-testable proof of the
 * security foundation.
 */
@RestController
@Profile({"dev", "test"})
public class WhoAmIController {

    public record WhoAmIResponse(String subject, String role, String merchantId) {
    }

    @GetMapping("/api/v1/dev/whoami")
    public WhoAmIResponse whoAmI(Principal principal) {
        AuthenticatedPrincipal p = (AuthenticatedPrincipal) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
        return new WhoAmIResponse(p.subject(), p.role().name(), p.merchantId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/dev/admin-only")
    public String adminOnly() {
        return "If you can read this, RBAC let an ADMIN through.";
    }
}
