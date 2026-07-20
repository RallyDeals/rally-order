package com.rally.order.config.kafka;

public final class KafkaTopics {

    private KafkaTopics() {
    }
    public static final String TEST_TOPIC = "test.connection-check";
    // =========================
    // Participant Service -> Order Service
    // =========================
    public static final String PARTICIPANT_JOINED = "participant.joined";
    public static final String PARTICIPANT_LEFT = "participant.left";

    // =========================
    // Deal Service -> Order Service
    // =========================
    public static final String DEAL_SUCCEEDED = "deal.succeeded";
    public static final String DEAL_FAILED = "deal.failed";

    // =========================
    // Order Service -> Payment Service
    // =========================
    public static final String ORDER_PAYMENT_INITIATION_REQUESTED =
            "order.payment_initiation_requested";

    public static final String ORDER_PAYMENT_SETTLEMENT_REQUESTED =
            "order.payment_settlement_requested";

    // =========================
    // Payment Service -> Order Service
    // =========================
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_CHARGED = "payment.charged";
    public static final String PAYMENT_CAPTURED = "payment.captured";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_VOIDED = "payment.voided";

    // =========================
    // Order Service -> Other Services
    // =========================
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_AUTHORIZED = "order.authorized";
    public static final String ORDER_NORMAL_CANCELLED = "order.normal_order_cancelled";
    public static final String ORDER_DEAL_CANCELLED = "order.deal_order_cancelled";
}