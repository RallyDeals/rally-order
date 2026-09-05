package com.rally.order.client.impl;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.InventoryServiceClient;
import com.rally.order.client.dto.InventoryReserveItem;
import com.rally.order.client.dto.InventoryReserveRequest;
import com.rally.order.client.dto.InventoryReserveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static java.lang.Integer.valueOf;

@Profile("dev")
@Component
public class InventoryServiceFakeClientImpl implements InventoryServiceClient {
    @Value("${inventory.service.fake-delay-ms:0}")
    private long fakeDelayMs;

    @Override
    public InventoryReserveResponse reserveInventory(InventoryReserveRequest request) {
        if (fakeDelayMs > 0) {
            try {
                Thread.sleep(fakeDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating inventory delay", e);
            }
            throw new ServiceUnavailableException("Inventory service is unavailable");
        }

        return InventoryReserveResponse.builder()
                .orderId(request.getOrderId())
                .items(request.getItems().stream()
                        .map(item -> InventoryReserveItem.builder()
                                .productId(item.getProductId())
                                .available(valueOf(item.getQuantity() % 2 ==0? 0: 100))
                                .reserved(Boolean.valueOf(item.getQuantity() % 2==1))
                                .build())
                        .toList())
                .build();
    }
}
