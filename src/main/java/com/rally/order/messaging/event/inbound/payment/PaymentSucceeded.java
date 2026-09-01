package com.rally.order.messaging.event.inbound.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceeded(
        UUID paymentId, UUID orderId, BigDecimal amount) {
}
