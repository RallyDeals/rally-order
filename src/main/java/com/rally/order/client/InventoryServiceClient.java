package com.rally.order.client;

import com.rally.order.client.dto.InventoryReserveRequest;
import com.rally.order.client.dto.InventoryReserveResponse;

public interface InventoryServiceClient {
    InventoryReserveResponse reserveInventory(InventoryReserveRequest request);
}
