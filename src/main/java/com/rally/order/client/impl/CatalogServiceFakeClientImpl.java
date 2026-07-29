package com.rally.order.client.impl;

import com.rally.order.client.CatalogServiceClient;
import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

@Profile("dev")
@Component
public class CatalogServiceFakeClientImpl implements CatalogServiceClient {
    @Override
    public CatalogLookupResponse lookup(CatalogLookupRequest request) {
        return CatalogLookupResponse.builder()
                .found(new HashMap<>() {{
                    request.getProductIds().forEach(productId -> put(productId, BigDecimal.valueOf(150.0)));
                }})
                .notFound(new ArrayList<>())
                .build();
    }
}
