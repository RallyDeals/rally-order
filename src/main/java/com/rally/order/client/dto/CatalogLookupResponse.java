package com.rally.order.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class CatalogLookupResponse {
    private Map<UUID, BigDecimal> found;
    private List<UUID> notFound;
}
