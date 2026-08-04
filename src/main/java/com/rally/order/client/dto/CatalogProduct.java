package com.rally.order.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class CatalogProduct {
    private UUID productId;
    private String name;
    private String imageUrl;
    private BigDecimal price;
}
