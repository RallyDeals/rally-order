package com.rally.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderProductResponse {
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private int quantity;
    private BigDecimal unitPrice;
}
