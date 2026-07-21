package com.rally.order.messaging.support;

public class EventTypes {
    public static final String PARTICIPANT_JOINED = "participant.joined";
    public static final String PARTICIPANT_LEFT = "participant.left";

    public static final String DEAL_SUCCEEDED = "deal.succeeded";
    public static final String DEAL_FAILED = "deal.failed";

    public static final String ORDER_PAYMENT_CHARGE_REQUIRED =
            "order.payment_charge_required";
    public static final String ORDER_PAYMENT_AUTHORIZE_REQUIRED =
            "order.payment_authorize_required";
    public static final String ORDER_PAYMENT_CAPTURE_REQUESTED =
            "order.payment_capture_requested";
    public static final String ORDER_PAYMENT_VOID_REQUESTED =
            "order.payment_void_requested";
    public static final String ORDER_PAYMENT_PAYMENT_TIMEOUT =
            "order.payment_timeout";

    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_CHARGED = "payment.charged";
    public static final String PAYMENT_CAPTURED = "payment.captured";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_VOIDED = "payment.voided";

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_AUTHORIZED = "order.authorized";
    public static final String ORDER_NORMAL_CANCELLED = "order.normal_order_cancelled";
    public static final String ORDER_DEAL_CANCELLED = "order.deal_order_cancelled";
}
