package com.rally.order.client.dto;


import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class InventoryReserveRequest {
    private UUID orderId;
    private UUID productId;
    private int quantity;
}
