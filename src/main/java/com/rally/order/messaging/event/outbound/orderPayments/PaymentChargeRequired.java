package com.rally.order.messaging.event.outbound.orderPayments;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentChargeRequired(UUID userId, UUID orderId, BigDecimal amount, String paymentIntentId) {
}
