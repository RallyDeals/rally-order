package com.rally.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProduct {
    private UUID id;
    private String name;
    private String imageUrl;
    private BigDecimal basePrice;
}
