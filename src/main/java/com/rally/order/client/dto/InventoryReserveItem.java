package com.rally.order.client.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveItem {
    private UUID productId;
    private Integer available;
    private Boolean reserved;
}
