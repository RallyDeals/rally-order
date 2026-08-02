package com.rally.order.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderProduct {
    private UUID productId;
    private int quantity;
    private BigDecimal unitPrice;
}
