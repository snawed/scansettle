package com.scansettle.api.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Every request gets an X-Correlation-Id, either propagated from the caller or
 * generated here. It is placed in MDC for structured logging, echoed on the
 * response, and available to the rest of the request via {@link CorrelationId}.
 *
 * <p>Must run before Spring Security's filter chain (registered at order -100,
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER}) — otherwise an authentication
 * failure is rejected before this filter ever runs, and the resulting error
 * response has no correlation ID. {@link Ordered#HIGHEST_PRECEDENCE} guarantees
 * that regardless of Spring Security's configured order.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            CorrelationId.set(correlationId);
            chain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
            MDC.remove(MDC_KEY);
        }
    }
}
