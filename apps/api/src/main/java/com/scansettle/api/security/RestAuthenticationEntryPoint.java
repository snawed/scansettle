package com.scansettle.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scansettle.api.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * An unauthenticated request never reaches the {@code @RestControllerAdvice} layer
 * (it's rejected in the security filter chain, before DispatcherServlet), so it
 * needs its own RFC 7807 rendering to keep the error contract consistent with
 * docs/api.md — see GlobalExceptionHandler for the equivalent 403 (RBAC-denied) case.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource.");
        problem.setType(URI.create("https://scansettle.com/problems/unauthenticated"));
        problem.setTitle("Unauthenticated");
        problem.setProperty("correlationId", CorrelationId.currentOrUnknown());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
