package com.scansettle.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless, JWT-bearer security. Public endpoints are the ones docs/api.md marks
 * as customer-facing/no-auth plus operational endpoints (health, API docs); every
 * other endpoint requires an authenticated principal, and merchant-scoped endpoints
 * additionally enforce role via {@code @PreAuthorize} at the controller/service layer
 * (see docs/security.md — authorization is never left to the frontend alone).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/dev/token",
            "/api/v1/open-banking/banks",
            // Merchant self-registration and login — no prior relationship yet.
            "/api/v1/merchants",
            "/api/v1/auth/login",
            "/api/v1/auth/mfa/verify-login",
            // ScanSettle's own internal ops/support login (Phase 8) — a distinct
            // persona from merchant users, see Role.PLATFORM_ADMIN.
            "/api/v1/admin/auth/login",
            "/api/v1/admin/auth/mfa/verify-login",
            // Customer payment journey — no ScanSettle account, ever (docs/api.md).
            "/api/v1/payment-links/*/public",
            "/api/v1/payment-links/*/payments",
            "/api/v1/payments/*/status",
            // ScanSettle Tables — the customer's QR scan and bill/split/tip journey
            // (docs/scansettle-tables.md). Single-segment "*" does not match the
            // merchant-only "/api/v1/bills/*/void" (two segments), so this stays
            // scoped to just the public bill read.
            "/api/v1/tables/scan/*",
            "/api/v1/bills/*",
            "/api/v1/bills/*/payments",
            // Real provider webhook ingress (Phase 4) — authenticated by HMAC
            // signature inside the handler, never a merchant JWT.
            "/api/v1/webhooks/open-banking",
            // Mock bank (dev/test only, profile-gated) — the local stand-in for a
            // real bank's own authentication/consent screen.
            "/api/v1/mock-bank/*",
            "/api/v1/mock-bank/*/decision",
            // Dev/test-only direct-mutation shortcut (Phase 3) — superseded for real
            // UI flows by the webhook path above, kept for fast test fixtures.
            "/api/v1/dev/payments/*/simulate-provider-status"
    };

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint) throws Exception {
        http
                // Bearer-token auth (Authorization header), never a cookie — CSRF exploits
                // the browser's automatic cookie attachment, which doesn't apply here, and
                // a cross-origin page can't read localStorage or set this header itself
                // (docs/security.md Phase 9 review).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Beyond Spring Security's defaults (X-Content-Type-Options, X-Frame-Options,
                // Cache-Control, disabled X-XSS-Protection) — CSP/Referrer-Policy/
                // Permissions-Policy aren't enabled by default and this is a pure JSON API
                // that also serves swagger-ui's own same-origin JS/CSS (docs/security.md
                // Phase 9 hardening).
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; object-src 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")));

        return http.build();
    }

    /**
     * The frontend (a separate origin — a different port even in local dev) calls
     * this API directly from the browser, so CORS must be explicit. Origins come
     * from config ({@code app.cors.allowed-origins} / {@code APP_CORS_ALLOWED_ORIGINS}),
     * never hardcoded or wildcarded, since credentials (the bearer token) are involved.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
