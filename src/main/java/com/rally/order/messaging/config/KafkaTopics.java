package com.rally.order.messaging.config;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    // Consumed by order service
    public static final String PAYMENT = "payment.events";
    public static final String PARTICIPATION = "participation";
    public static final String DEAL = "deal";

    // Published by order service
    public static final String ORDER_PAYMENTS = "order.payments_requested";
    public static final String ORDER_EVENTS = "order.lifecycle_events";

    // Header keys used on every message, both consumed and published.
    public static final String HEADER_EVENT_ID = "X-Id";
    public static final String HEADER_EVENT_TYPE = "X-Type";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_CAUSATION_ID = "X-Causation-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
}