package com.rally.order.support;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Holds the per-thread correlation id in MDC so logging and tracing can
 * stay consistent across HTTP, Kafka, and internal service calls.
 * It is populated when a request or message enters the application and is
 * removed again when the request/message lifecycle ends.
 */
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