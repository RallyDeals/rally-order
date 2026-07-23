package com.rally.order.dto;

import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutOrderResponse {
    private UUID id;
    private UUID userId;
    private List<OrderProduct> orderProducts;
    private OrderType orderType;
    private OrderStatus status;
    private BigDecimal totalPrice;
    private OffsetDateTime createdAt;
}
