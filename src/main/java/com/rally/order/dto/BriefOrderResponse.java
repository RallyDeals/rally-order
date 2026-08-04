package com.rally.order.dto;

import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BriefOrderResponse {
    private UUID orderId;
    private OrderType orderType;
    private OrderStatus status;
    private UUID dealId;
    private int noOfItems;
    private BigDecimal totalPrice;
}
