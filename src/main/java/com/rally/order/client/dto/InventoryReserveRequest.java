package com.rally.order.client.dto;


import com.rally.order.dto.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InventoryReserveRequest {
    private UUID orderId;
    private List<OrderItem> items;
}
