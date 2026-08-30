package com.scansettle.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain errors that should be rendered as RFC 7807 Problem Details.
 * {@code problemType} becomes the "type" URI suffix (e.g. "insufficient-remaining-balance").
 */
public class ApplicationException extends RuntimeException {

    private final HttpStatus status;
    private final String problemType;

    public ApplicationException(HttpStatus status, String problemType, String message) {
        super(message);
        this.status = status;
        this.problemType = problemType;
    }

    public HttpStatus status() {
        return status;
    }

    public String problemType() {
        return problemType;
    }
}
