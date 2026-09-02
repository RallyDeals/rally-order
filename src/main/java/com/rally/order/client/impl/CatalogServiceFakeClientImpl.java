package com.rally.order.client.impl;

import com.rally.common.exceptions.shared.ServiceUnavailableException;
import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
import com.rally.order.client.dto.CatalogProduct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

@Profile("dev")
@Component
public class CatalogServiceFakeClientImpl implements CatalogServiceClient {

    private static final UUID NOT_FOUND_PRODUCT_ID =
            UUID.fromString("e87efa74-4903-43f7-9d23-8687d6cd54f3");

    @Value("${category.service.fake-delay-ms:0}")
    private long fakeDelayMs;

    @Override
    public CatalogLookupResponse lookup(CatalogLookupRequest request) {
        if (fakeDelayMs > 0) {
            try {
                Thread.sleep(fakeDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating catalog delay", e);
            }
            throw new ServiceUnavailableException("Catalog service is unavailable");
        }

        HashMap<UUID, CatalogProduct> found = new HashMap<>();
        ArrayList<UUID> notFound = new ArrayList<>();

        request.getProductIds().forEach(productId -> {
            if (productId.equals(NOT_FOUND_PRODUCT_ID)) {
                notFound.add(productId);
            } else {
                found.put(productId, CatalogProduct.builder()
                        .id(productId)
                        .sellerId(UUID.nameUUIDFromBytes(("seller-" + productId).getBytes()))
                        .name("Fake Product " + productId)
                        .imageUrl("https://picsum.photos/seed/" + productId + "/200")
                        .basePrice(BigDecimal.valueOf(150.0))
                        .build());
            }
        });

        return CatalogLookupResponse.builder()
                .found(found)
                .notFound(notFound)
                .build();
    }
}
