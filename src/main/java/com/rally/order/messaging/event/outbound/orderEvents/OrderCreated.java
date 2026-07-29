package com.rally.order.messaging.event.outbound.orderEvents;

import com.rally.order.dto.OrderItem;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreated(UUID orderId, UUID userId, List<OrderItem> items) {
}
