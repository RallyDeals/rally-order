package com.rally.order.client.impl;

import com.rally.common.exceptions.shared.InternalServerErrorException;
import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.InventoryServiceClient;
import com.rally.order.client.dto.InventoryReserveRequest;
import com.rally.order.client.dto.InventoryReserveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Profile("prod")
@Component
@RequiredArgsConstructor
public class InventoryServiceClientImpl implements InventoryServiceClient {
    private final RestTemplate restTemplate;

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    @Override
    public InventoryReserveResponse reserveInventory(InventoryReserveRequest request) {
        String url = inventoryServiceUrl + "/order-reserve";

        try {
            ResponseEntity<InventoryReserveResponse> response =
                    restTemplate.postForEntity(url, request, InventoryReserveResponse.class);
            return response.getBody();
        } catch (ResourceAccessException resourceAccessException) {
            throw new ServiceUnavailableException("Inventory service is unavailable");
        } catch(HttpServerErrorException serverErrorException){
            throw new InternalServerErrorException("Inventory service returned server error");
        }
    }
}
