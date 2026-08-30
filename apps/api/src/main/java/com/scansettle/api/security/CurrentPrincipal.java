package com.scansettle.api.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single place a controller/service reads "who is calling and which merchant
 * are they scoped to". Every merchant-scoped repository call must go through
 * {@link #merchantId()} rather than trust an id in the request — this is what makes
 * tenant isolation (docs/security.md Section 21) structural rather than a
 * per-endpoint convention someone can forget.
 */
@Component
public class CurrentPrincipal {

    public AuthenticatedPrincipal get() {
        var auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authenticated principal on this thread");
        }
        return (AuthenticatedPrincipal) auth.getPrincipal();
    }

    public UUID merchantId() {
        String merchantId = get().merchantId();
        if (merchantId == null) {
            throw new IllegalStateException("Authenticated principal has no merchant context");
        }
        return UUID.fromString(merchantId);
    }

    public UUID userId() {
        String userId = get().userId();
        if (userId == null) {
            throw new IllegalStateException("Authenticated principal has no merchant-user id");
        }
        return UUID.fromString(userId);
    }

    public String subject() {
        return get().subject();
    }
}
