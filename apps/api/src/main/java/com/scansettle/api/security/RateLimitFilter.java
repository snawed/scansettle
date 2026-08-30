package com.scansettle.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.common.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Per-IP token bucket on the genuinely public, unauthenticated endpoints — merchant/
 * ops login and registration (brute-force/spam), and the anonymous customer payment
 * journey (scraping/abuse) — docs/security.md's rate-limiting control. Deliberately
 * excludes health/docs/dev endpoints (no abuse value in limiting them) and the
 * provider webhook ingress (already authenticated by HMAC signature, and a real
 * provider may legitimately burst-deliver many events from one IP).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> LIMITED_PATTERNS = List.of(
            "/api/v1/merchants",
            "/api/v1/auth/login",
            "/api/v1/auth/mfa/verify-login",
            "/api/v1/admin/auth/login",
            "/api/v1/admin/auth/mfa/verify-login",
            "/api/v1/payment-links/*/public",
            "/api/v1/payment-links/*/payments",
            "/api/v1/payments/*/status",
            "/api/v1/tables/scan/*",
            "/api/v1/bills/*",
            "/api/v1/bills/*/payments"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper,
                            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Disabled under the test profile (application-test.yml) — integration tests
        // legitimately fire many rapid requests at these exact paths (merchant
        // registration, bill payments) from the same loopback address, and rate
        // limiting the test suite itself would be a false-positive source of test
        // flakiness, not a real safeguard.
        if (enabled && isLimited(request.getRequestURI()) && !rateLimiter.tryConsume(request.getRemoteAddr())) {
            writeTooManyRequests(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isLimited(String path) {
        return LIMITED_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests — please slow down and try again shortly.");
        problem.setType(URI.create("https://scansettle.com/problems/rate-limited"));
        problem.setTitle("Too Many Requests");
        problem.setProperty("correlationId", CorrelationId.currentOrUnknown());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
