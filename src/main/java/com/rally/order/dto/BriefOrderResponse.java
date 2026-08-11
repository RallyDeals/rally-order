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
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BriefOrderResponse {
    private UUID orderId;
    private OrderType orderType;
    private OrderStatus status;
    private ShippingStatus shippingStatus;
    private OffsetDateTime createdAt;
    private int noOfItems;
    private BigDecimal totalPrice;
}
