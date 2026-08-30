package com.scansettle.api.common.error;

import com.scansettle.api.common.web.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Renders every error as RFC 7807 Problem Details (application/problem+json),
 * per docs/api.md. Every response carries the request's correlation ID so a
 * customer-reported error can be traced through logs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE_URI = "https://scansettle.com/problems/";

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplicationException(ApplicationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URI + ex.problemType()));
        problem.setTitle(humanizeTitle(ex.problemType()));
        attachCorrelationId(problem);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request failed validation.");
        problem.setType(URI.create(PROBLEM_BASE_URI + "validation-failed"));
        problem.setTitle("Validation failed");
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        problem.setProperty("errors", errors);
        attachCorrelationId(problem);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
        problem.setType(URI.create(PROBLEM_BASE_URI + "forbidden"));
        problem.setTitle("Forbidden");
        attachCorrelationId(problem);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Never leak internal exception detail to the caller — log it, return a generic message.
        log.error("Unhandled exception [correlationId={}]", CorrelationId.currentOrUnknown(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        problem.setType(URI.create(PROBLEM_BASE_URI + "internal-error"));
        problem.setTitle("Internal error");
        attachCorrelationId(problem);
        return problem;
    }

    private void attachCorrelationId(ProblemDetail problem) {
        problem.setProperty("correlationId", CorrelationId.currentOrUnknown());
    }

    private String humanizeTitle(String problemType) {
        String[] words = problemType.split("-");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
