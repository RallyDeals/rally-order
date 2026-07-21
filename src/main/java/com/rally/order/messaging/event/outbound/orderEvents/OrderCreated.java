package com.rally.order.messaging.event.outbound.orderEvents;

import java.util.List;
import java.util.UUID;

public record OrderCreated(UUID orderId, UUID userId, List<Object> items) {
}
