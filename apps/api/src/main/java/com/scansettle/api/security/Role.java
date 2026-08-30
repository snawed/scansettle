package com.scansettle.api.security;

/**
 * Merchant-user RBAC roles (docs/security.md). Every authenticated merchant-scoped
 * endpoint is authorized against one of these — enforced server-side via
 * {@code @PreAuthorize}, never left to the frontend to hide a button.
 *
 * <p>{@code PLATFORM_ADMIN} is a deliberately separate, standalone role for
 * ScanSettle's own internal ops/support staff (docs/architecture.md persona
 * "ScanSettle Ops/Support") — it is NOT part of the OWNER>ADMIN>STAFF>READ_ONLY
 * merchant hierarchy ({@link RoleHierarchyConfig}), so a platform admin's token can
 * never satisfy a merchant-scoped {@code hasRole('ADMIN')} check and vice versa.
 */
public enum Role {
    OWNER,
    ADMIN,
    STAFF,
    READ_ONLY,
    PLATFORM_ADMIN
}
