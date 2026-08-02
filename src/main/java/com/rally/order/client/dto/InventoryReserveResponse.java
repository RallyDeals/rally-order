package com.rally.order.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InventoryReserveResponse {
    private UUID orderId;
    private List<InventoryReserveItem> items;
}
