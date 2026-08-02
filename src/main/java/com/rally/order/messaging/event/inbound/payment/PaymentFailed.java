package com.rally.order.messaging.event.inbound.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentFailed(
        UUID paymentId, String paymentIntentId, UUID orderId, BigDecimal amount, String errorMessage, String errorCode) {
}
