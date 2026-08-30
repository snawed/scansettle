package com.scansettle.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

/**
 * OWNER > ADMIN > STAFF > READ_ONLY (docs/security.md RBAC matrix). A higher role
 * implicitly has every permission a lower role has, so
 * {@code @PreAuthorize("hasRole('STAFF')")} reads as "STAFF or above" without
 * spelling out every role at each call site.
 */
@Configuration
public class RoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("OWNER").implies("ADMIN")
                .role("ADMIN").implies("STAFF")
                .role("STAFF").implies("READ_ONLY")
                .build();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
