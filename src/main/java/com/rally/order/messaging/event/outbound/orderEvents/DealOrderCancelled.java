package com.rally.order.messaging.event.outbound.orderEvents;

import java.util.List;
import java.util.UUID;

public record DealOrderCancelled(UUID orderId, UUID dealId, UUID participantId, UUID userId,
                                 List<Object> items, String reason) {
}
