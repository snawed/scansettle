package com.scansettle.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Used for state conflicts such as a bill-payment reservation that no longer fits
 * the remaining balance (see docs/scansettle-tables.md).
 */
public class ConflictException extends ApplicationException {
    public ConflictException(String problemType, String message) {
        super(HttpStatus.CONFLICT, problemType, message);
    }
}
