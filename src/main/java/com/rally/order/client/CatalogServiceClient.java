package com.rally.order.client;

import com.rally.order.client.dto.CatalogLookupRequest;
import com.rally.order.client.dto.CatalogLookupResponse;

public interface CatalogServiceClient {
    CatalogLookupResponse lookup(CatalogLookupRequest request);
}
