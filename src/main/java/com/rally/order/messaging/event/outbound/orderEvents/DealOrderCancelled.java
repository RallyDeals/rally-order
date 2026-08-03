package com.rally.order.messaging.event.outbound.orderEvents;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record DealOrderCancelled(UUID orderId, UUID dealId, UUID participantId, UUID userId,
                                 String reason) {
}
