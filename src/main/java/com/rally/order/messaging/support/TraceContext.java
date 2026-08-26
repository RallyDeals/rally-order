package com.rally.order.messaging.support;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

    public static final String CORRELATION_ID = "correlationId";

    private TraceContext() {
    }

    public static void put(UUID correlationId) {
        MDC.put(CORRELATION_ID, correlationId.toString());
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID);
    }

    public static UUID correlationId() {
        return require(CORRELATION_ID);
    }

    private static UUID require(String key) {
        String value = MDC.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    "Missing MDC key '" + key + "' - TraceContext was never populated for this thread");
        }
        return UUID.fromString(value);
    }
}