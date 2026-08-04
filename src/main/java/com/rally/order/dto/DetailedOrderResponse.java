package com.rally.order.dto;

import com.rally.order.model.CancelReason;
import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetailedOrderResponse {
    private UUID orderId;
    private UUID userId;
    private OrderType orderType;
    private UUID dealId;
    private UUID participantId;
    private OrderStatus status;
    private CancelReason cancelReason;
    private BigDecimal totalPrice;
    private UUID paymentId;
    private String paymentIntentId;
    private List<OrderProductResponse> orderProducts;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime statusUpdatedAt;
}
