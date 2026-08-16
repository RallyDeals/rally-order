package com.rally.order.dto;

import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import com.rally.order.model.ShippingStatus;
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
public class BriefSellerOrderResponse {
    private UUID orderId;
    private CompactedOrderStatus status;
    private OffsetDateTime createdAt;
    private List<BriefSellerOrderItemResponse> items;
}
