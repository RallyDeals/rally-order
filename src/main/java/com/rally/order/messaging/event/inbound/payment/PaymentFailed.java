package com.rally.order.messaging.event.inbound.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentFailed(
        UUID paymentId, String paymentIntentId, String orderId, BigDecimal amount, String error) {
}
