package com.rally.order.messaging.event.outbound.orderPayments;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentChargeRequired(UUID userId, UUID orderId, BigDecimal amount, String paymentMethodId) {
}
