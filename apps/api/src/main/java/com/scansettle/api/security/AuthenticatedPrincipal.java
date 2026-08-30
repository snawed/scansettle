package com.scansettle.api.security;

/**
 * The authenticated identity for the lifetime of one request. {@code subject} is an
 * opaque user identifier (an email for MVP); {@code merchantId} is null for
 * ScanSettle ops/internal principals. {@code userId} is the {@code MerchantUser.id}
 * — null for dev/test principals not backed by a real row.
 */
public record AuthenticatedPrincipal(String subject, Role role, String merchantId, String userId) {

    public AuthenticatedPrincipal(String subject, Role role, String merchantId) {
        this(subject, role, merchantId, null);
    }
}
