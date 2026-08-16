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
public class BriefSellerOrderItemResponse {
    UUID productId;
    String productName;
    String productImageUrl;
    int quantity;
    BigDecimal unitPrice;
}
