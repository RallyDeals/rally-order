package com.rally.order.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class InventoryReserveItem {
    private UUID productId;
    private int available;
    private boolean reserved;
}
