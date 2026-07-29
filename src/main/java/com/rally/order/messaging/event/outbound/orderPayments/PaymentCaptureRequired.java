package com.rally.order.messaging.event.outbound.orderPayments;

import java.util.UUID;

public record PaymentCaptureRequired(UUID orderId, UUID paymentId) {
}
