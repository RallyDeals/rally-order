package com.rally.order.messaging.event.outbound.orderEvents;

import com.rally.order.dto.OrderProductResponse;
import com.rally.order.model.CancelReason;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record NormalOrderCancelled(UUID orderId, UUID userId, CancelReason cancelReason, List<OrderProductResponse> items,
                                    BigDecimal totalPrice, String paymentErrorMessage) {
}
