package com.rally.order.messaging.support;

public class EventTypes {
    public static final String PARTICIPANT_JOINED = "participant.joined";
    public static final String PARTICIPANT_LEFT = "participant.left";

    public static final String DEAL_SUCCEEDED = "deal.succeeded";
    public static final String DEAL_FAILED = "deal.failed";

    public static final String ORDER_PAYMENT_CHARGE_REQUIRED =
            "Payment.SettlementRequired.Charge";
    public static final String ORDER_PAYMENT_AUTHORIZE_REQUIRED =
            "Payment.SettlementRequired.Authorize";
    public static final String ORDER_PAYMENT_CAPTURE_REQUESTED =
            "Payment.SettlementRequired.Capture";
    public static final String ORDER_PAYMENT_VOID_REQUESTED =
            "Payment.SettlementRequired.Void";
    public static final String ORDER_PAYMENT_PAYMENT_TIMEOUT =
            "Payment.Timeout";

    public static final String PAYMENT_AUTHORIZED = "Payment.Authorized";
    public static final String PAYMENT_CHARGED = "Payment.Charged";
    public static final String PAYMENT_CAPTURED = "Payment.Captured";
    public static final String PAYMENT_FAILED = "Payment.Failed";
    public static final String PAYMENT_VOIDED = "Payment.Voided";

    public static final String ORDER_CREATED = "Order.Created";
    public static final String ORDER_AUTHORIZED = "Order.Authorized";
    public static final String ORDER_NORMAL_CANCELLED = "Order.NormalCancelled";
    public static final String ORDER_DEAL_CANCELLED = "Order.DealCancelled";
}
