package com.rally.order.messaging.support;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CAUSATION_ID = "causationId";
    public static final String TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static void put(UUID correlationId, UUID causationId, UUID traceId) {
        MDC.put(CORRELATION_ID, correlationId.toString());
        MDC.put(CAUSATION_ID, causationId.toString());
        MDC.put(TRACE_ID, traceId.toString());
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(CAUSATION_ID);
        MDC.remove(TRACE_ID);
    }

    public static UUID correlationId() {
        return require(CORRELATION_ID);
    }

    public static UUID causationId() {
        return require(CAUSATION_ID);
    }

    public static UUID traceId() {
        return require(TRACE_ID);
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