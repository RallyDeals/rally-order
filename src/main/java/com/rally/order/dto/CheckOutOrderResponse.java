package com.rally.order.dto;

import com.rally.order.model.OrderStatus;
import com.rally.order.model.OrderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutOrderResponse {
    private UUID id;
    private UUID userId;
    @Builder.Default
    private List<OrderProduct> orderProducts = new ArrayList<>();
    @Builder.Default
    private OrderType orderType = OrderType.NORMAL;
    @Builder.Default
    private OrderStatus orderStatus = OrderStatus.PENDING_CHARGE;
    private BigDecimal totalPrice;
    private OffsetDateTime createdAt;
}
