package com.rally.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerOrdersAnalytics {
    private int totalOrders;
    private BigDecimal revenue;
    private int pendingOrders;
    private int deliveredOrders;
}
