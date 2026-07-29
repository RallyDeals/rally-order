package com.rally.order.client.impl;

import com.rally.order.client.InventoryServiceClient;
import com.rally.order.client.dto.InventoryReserveRequest;
import com.rally.order.client.dto.InventoryReserveResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
public class InventoryServiceFakeClientImpl implements InventoryServiceClient {
    @Override
    public InventoryReserveResponse reserveInventory(InventoryReserveRequest request) {
        return InventoryReserveResponse.builder()
                .productId(request.getProductId())
                .available(100)
                .reserved(true)
                .build();
    }
}
