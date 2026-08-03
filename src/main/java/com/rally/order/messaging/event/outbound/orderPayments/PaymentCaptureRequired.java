package com.rally.order.messaging.event.outbound.orderPayments;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PaymentCaptureRequired(UUID orderId, UUID paymentId) {
}
