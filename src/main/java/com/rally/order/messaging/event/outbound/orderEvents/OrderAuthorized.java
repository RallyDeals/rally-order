package com.rally.order.messaging.event.outbound.orderEvents;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderAuthorized(UUID orderId, UUID dealId, UUID userId, BigDecimal totalPrice) {
}
