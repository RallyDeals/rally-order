package com.rally.order.client.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveResponse {
    private UUID orderId;
    private List<InventoryReserveItem> items;
}
