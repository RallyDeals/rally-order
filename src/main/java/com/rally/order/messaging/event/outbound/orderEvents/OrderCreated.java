package com.rally.order.messaging.event.outbound.orderEvents;

import com.rally.order.dto.OrderProductResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreated(UUID orderId, UUID userId, List<OrderProductResponse> items, BigDecimal totalPrice, String address) {
}
