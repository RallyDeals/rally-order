package com.rally.order.messaging.event.outbound.orderEvents;

import com.rally.order.dto.OrderProductResponse;
import com.rally.order.model.CancelReason;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record DealOrderCancelled(UUID orderId, UUID dealId, UUID participantId, UUID userId,
                                 CancelReason reason, List<OrderProductResponse> items, BigDecimal totalPrice) {
}
