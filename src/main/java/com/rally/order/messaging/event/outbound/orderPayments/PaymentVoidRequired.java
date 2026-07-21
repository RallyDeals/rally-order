package com.rally.order.messaging.event.outbound.orderPayments;

import java.util.UUID;

public record PaymentVoidRequired(UUID paymentId) {
}
