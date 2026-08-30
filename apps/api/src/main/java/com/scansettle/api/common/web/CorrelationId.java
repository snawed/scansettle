package com.scansettle.api.common.web;

/** Request-scoped correlation ID, set by {@link CorrelationIdFilter}. */
public final class CorrelationId {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationId() {
    }

    static void set(String value) {
        CURRENT.set(value);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static String currentOrUnknown() {
        String value = CURRENT.get();
        return value != null ? value : "unknown";
    }
}
